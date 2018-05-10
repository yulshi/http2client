package com.yulong.http2.client.netty;

import static com.yulong.http2.client.common.Constants.CONNECTION_STREAM_ID;
import static com.yulong.http2.client.utils.LogUtil.log;
import static com.yulong.http2.client.utils.Utils.combine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.yulong.http2.client.Connection;
import com.yulong.http2.client.ConnectionException;
import com.yulong.http2.client.Stream;
import com.yulong.http2.client.StreamException;
import com.yulong.http2.client.common.ErrorCodeRegistry;
import com.yulong.http2.client.frame.ContinuationFrame;
import com.yulong.http2.client.frame.DataFrame;
import com.yulong.http2.client.frame.Frame;
import com.yulong.http2.client.frame.FrameHistory;
import com.yulong.http2.client.frame.HeadersFrame;
import com.yulong.http2.client.frame.PriorityFrame;
import com.yulong.http2.client.frame.PushPromiseFrame;
import com.yulong.http2.client.frame.ResetFrame;
import com.yulong.http2.client.frame.WindowUpdateFrame;
import com.yulong.http2.client.message.Http2Headers;
import com.yulong.http2.client.message.Http2Response;
import com.yulong.http2.client.message.PushRequest;

/**
 * An HTTP/2 Stream representation
 * 
 * @author yushi
 * @since Jan 11 2016
 *
 */
public class NettyStream implements Stream {

	private final Http2FrameHandler connection;
	private final int id;
	private final AtomicReference<State> state;

	private NettyStream parentStream;
	private final List<NettyStream> dependentStreams;
	private int weight;

	private final AtomicReference<ResetFrame> receivedResetFrame = new AtomicReference<>();

	private byte[] receivedHeaderBlock;
	private byte[] receivedData;

	private Http2Response response;

	private final List<FrameHistory> allReceivedFrames;
	private final ExecutorService executorService;

	private Stream currentPromisedStream;
	private PushPromiseFrame currentPushPromiseFrame;
	private final Map<PushRequest, Stream> promisedStreams;
	private final List<Consumer<PushRequest>> pushRequestConsumers;

	NettyStream(Http2FrameHandler connection, int id) {
		this(connection, id, State.IDLE);
	}

	NettyStream(Http2FrameHandler connection, int id, State state) {

		// Coalition:
		this.connection = connection;
		this.id = id;
		this.state = new AtomicReference<>(state);

		// Dependencies and priority:
		if (id == CONNECTION_STREAM_ID) {
			this.parentStream = null;
		} else {
			this.parentStream = connection.getConnectionStream();
			this.parentStream.dependentStreams.add(this);
		}
		this.dependentStreams = new ArrayList<NettyStream>();
		this.weight = 16;

		// Responses:
		this.receivedHeaderBlock = new byte[0];
		this.receivedData = new byte[0];

		this.allReceivedFrames = new ArrayList<>();

		executorService = Executors.newCachedThreadPool(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setDaemon(true);
				return t;
			}
		});

		promisedStreams = new HashMap<>();
		pushRequestConsumers = new ArrayList<>();

	}

	// ///////////////////////////////////////////////////////////////////////////
	// Stream properties
	// ///////////////////////////////////////////////////////////////////////////

	/**
	 * Get the stream id
	 * 
	 * @return
	 */
	@Override
	public int getId() {
		return id;
	}

	/**
	 * Get the connection on which the stream lives
	 * 
	 * @return
	 */
	@Override
	public Connection getConnection() {
		return connection;
	}

	/**
	 * Set the current stream state to the give value
	 * 
	 * @return
	 */
	@Override
	public State getState() {
		return this.state.get();
	}

	/**
	 * Send a HEADERS frame on the stream
	 * 
	 * @param headersFrame
	 * @throws ConnectionException
	 */
	@Override
	public void headers(HeadersFrame headersFrame) throws ConnectionException {

		connection.closeUnusedIdleStreams(id);

		if (state.compareAndSet(State.IDLE, State.OPEN)) {
			// a new request begins
		}
		connection.send(headersFrame);

		if (headersFrame.isEndHeaders()) {
			// End sending request headers
		}

		if (headersFrame.isPriority()) {
			prioritize(headersFrame.getPriorityFrame());
		}

		if (headersFrame.isEndStream()) {
			endStreamLocally();
		}

	}

	/**
	 * Send a PRIORITY frame on the stream
	 * 
	 * @param priorityFrame
	 * @throws ConnectionException
	 */
	public void priority(PriorityFrame priorityFrame) throws ConnectionException {
		prioritize(priorityFrame);
		connection.send(priorityFrame);
	}

	/**
	 * Send a CONTINUATION frame on the stream
	 * 
	 * @param continuationFrame
	 * @throws ConnectionException
	 */
	@Override
	public void continuation(ContinuationFrame continuationFrame) throws ConnectionException {
		connection.send(continuationFrame);
		if (continuationFrame.isEndHeaders()) {
			// End sending request headers
		}
	}

	/**
	 * Send a DATA frame on the stream
	 * 
	 * @param dataFrame
	 * @throws ConnectionException
	 */
	@Override
	public void data(DataFrame dataFrame) throws ConnectionException {

		connection.send(dataFrame);
		if (dataFrame.isEndStream()) {
			endStreamLocally();
		}

	}

	/**
	 * Send a RST_STREAM frame on the stream
	 * 
	 * @param errorCode
	 */
	@Override
	public void reset(ErrorCodeRegistry errorCode) {
		try {
			connection.send(new ResetFrame(id, errorCode));
		} catch (ConnectionException e) {
			log("Failed to send RST_STREAM due to " + e);
		}
		state.set(State.RESET_LOCAL);
	}

	/**
	 * Send a WINDOW_UPDATE frame with the give increment
	 * 
	 * @param windowSizeIncrement
	 * @throws ConnectionException
	 */
	public void windowUpdate(int windowSizeIncrement) throws ConnectionException {
		connection.send(new WindowUpdateFrame(id, windowSizeIncrement));
	}

	// ///////////////////////////////////////////////////////////////////////////
	// Stream Dependencies
	// ///////////////////////////////////////////////////////////////////////////
	/**
	 * Get the parent stream
	 * 
	 * @return
	 */
	@Override
	public Stream getParentStream() {
		return parentStream;
	}

	/**
	 * Get the dependent streams
	 * 
	 * @return
	 */
	@Override
	public List<NettyStream> getDependentStreams() {
		return Collections.unmodifiableList(dependentStreams);
	}

	/**
	 * Get the weight of the stream
	 * 
	 * @return
	 */
	@Override
	public int getWeight() {
		return weight;
	}

	@Override
	public void close() {
		log("---------- releasing resources occupied by the current stream: " + id + " ----------");
		executorService.shutdown();
	}

	// ///////////////////////////////////////////////////////////////////////////
	// Request/Response related methods
	// ///////////////////////////////////////////////////////////////////////////
	/**
	 * Get a future instance of Http2Response of the stream. It's useful when
	 * you'd like to get the response of the stream
	 * 
	 * @return
	 */
	@Override
	public Future<Http2Response> getResponseFuture() {
		return executorService.submit(() -> {
			while (response == null || !response.isComplete()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
			return response;
		});
	}

	/**
	 * Get all the received frames on the stream
	 * 
	 * @return
	 */
	@Override
	public List<FrameHistory> allReceivedFrames() {
		return Collections.unmodifiableList(this.allReceivedFrames);
	}

	/**
	 * If this stream is reset by peer, return the received RST_STREAM frame
	 * 
	 * @return
	 */
	@Override
	public ResetFrame resetByPeer() {
		return receivedResetFrame.get();
	}

	/**
	 * Return all the promised streams from this stream.
	 * 
	 * @return
	 */
	@Override
	public Map<PushRequest, Stream> promisedStreams() {
		return Collections.unmodifiableMap(promisedStreams);
	}

	/**
	 * Add a listenr which will be called on receipt of PUSH_RPOMISE frame
	 * 
	 * @param ppl
	 */
	@Override
	public void addPushRequestConsumer(Consumer<PushRequest> consumer) {
		pushRequestConsumers.add(consumer);
	}

	/**
	 * Process the received HEADERS frame
	 * 
	 * @param headersFrame
	 * @throws ConnectionException
	 */
	void onHeaders(HeadersFrame headersFrame) throws ConnectionException {

		currentPromisedStream = null;

		if (state.compareAndSet(State.RESERVED_REMOTE, State.HALF_CLOSED_LOCAL)) {
			// this is a PUSH stream sent from Server
		}

		if (headersFrame.isEndStream()) {
			endStreamRemotely();
		} else {

		}

		receivedHeaderBlock = headersFrame.getHeaderBlockFragment();
		if (headersFrame.isEndHeaders()) {
			processResponseHeaders();
		}

		if (headersFrame.isPriority()) {
			prioritize(headersFrame.getPriorityFrame());
		}

	}

	/**
	 * Process the received CONTINUATION frame
	 * 
	 * @param continuationFrame
	 * @throws ConnectionException
	 */
	void onContinuation(ContinuationFrame continuationFrame) throws ConnectionException {

		receivedHeaderBlock = combine(receivedHeaderBlock, continuationFrame.getHeaderBlockFragment());

		if (continuationFrame.isEndHeaders()) {
			if (currentPromisedStream == null) {
				processResponseHeaders();
			} else {
				processPushRequest();
			}
		}

	}

	/**
	 * Process the received DATA frame
	 * 
	 * @param dataFrame
	 */
	void onData(DataFrame dataFrame) {

		receivedData = combine(receivedData, dataFrame.getData());

		if (dataFrame.isEndStream()) {
			endStreamRemotely();
			response.setEntity(receivedData);
			response.setComplete(true);
		}

	}

	/**
	 * Process the received PRIORITY frame
	 * 
	 * @param priorityFrame
	 */
	void onPriority(PriorityFrame priorityFrame) {
		prioritize(priorityFrame);
	}

	/**
	 * Process the received WINDOW_UPDATE frame
	 * 
	 * @param windowUpdateFrame
	 * @throws StreamException
	 */
	void onWindowUpdate(WindowUpdateFrame windowUpdateFrame) throws StreamException {

		if (windowUpdateFrame.getWindowSizeIncrement() <= 0) {
			throw new StreamException(id, ErrorCodeRegistry.PROTOCOL_ERROR,
					"The flow-control window increment should not be 0 or negative: "
							+ windowUpdateFrame.getWindowSizeIncrement());
		}

	}

	/**
	 * Process the received PUSH_PROMISE frame
	 * 
	 * @param pushPromiseFrame
	 * @throws ConnectionException 
	 */
	void onPushPromise(PushPromiseFrame pushPromiseFrame, Stream promisedStream) throws ConnectionException {

		currentPushPromiseFrame = pushPromiseFrame;
		currentPromisedStream = promisedStream;

		connection.closeUnusedIdleStreams(id);

		receivedHeaderBlock = pushPromiseFrame.getHeaderBlockFragment();
		if (pushPromiseFrame.isEndHeaders()) {
			processPushRequest();
		}

	}

	/**
	 * Process the received RST_STREAM frame
	 * 
	 * @param resetFrame
	 */
	void onReset(ResetFrame resetFrame) {
		this.receivedResetFrame.set(resetFrame);
		state.set(State.RESET_REMOTE);
	}

	/**
	 * Get the current state of the stream
	 * 
	 * @param state
	 */
	void setState(State state) {
		this.state.set(state);
	}

	/**
	 * Record a received frame
	 * 
	 * @param frame
	 */
	void addReceivedFrame(Frame frame) {
		this.allReceivedFrames.add(new FrameHistory(frame));
	}

	/**
	 * An expressive text showing the stream
	 * 
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("ID: ").append(id).append(", ");
		sb.append("State: ").append(state.get().name()).append(", ");
		sb.append("Parent: ").append(parentStream != null ? parentStream.getId() : "null").append(", ");
		sb.append("Dependents: ");
		if (dependentStreams.isEmpty()) {
			sb.append("[]");
		} else {
			sb.append("[");
			boolean first = true;
			for (Stream dependent : dependentStreams) {
				if (!first) {
					sb.append(",");
				}
				sb.append(dependent.getId());
				first = false;
			}
			sb.append("]");
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public boolean equals(Object obj) {
		NettyStream another = (NettyStream) obj;
		return this.id == another.id;
	}

	@Override
	public int hashCode() {
		return this.id;
	}

	/**
	 * At the time the header block is received completely, this method gets
	 * called
	 * 
	 * @throws ConnectionException
	 */
	private void processResponseHeaders() throws ConnectionException {

		Http2Headers headers = connection.decode(receivedHeaderBlock);
		// log(headers);
		response = new Http2Response(headers);

		if (state.get() == State.CLOSED) {
			response.setComplete(true);
		}

	}

	/**
	 * At the time the header block is received completely for PUSH, this method gets
	 * called
	 * 
	 * @throws ConnectionException
	 */
	private void processPushRequest() throws ConnectionException {

		PushRequest pushRequest = new PushRequest(currentPushPromiseFrame.getStreamId(),
				currentPushPromiseFrame.getPromisedStreamId(), connection.decode(receivedHeaderBlock));

		for (Consumer<PushRequest> consumer : pushRequestConsumers) {
			consumer.accept(pushRequest);
		}

		promisedStreams.put(pushRequest, currentPromisedStream);

	}

	private void endStreamLocally() {
		if (state.compareAndSet(State.OPEN, State.HALF_CLOSED_LOCAL)) {
			// request ends
		}
	}

	private void endStreamRemotely() {
		if (state.compareAndSet(State.OPEN, State.HALF_CLOSED_REMOTE)) {
			// this will not happen on client side
		}
		if (state.compareAndSet(State.HALF_CLOSED_LOCAL, State.CLOSED)) {
			// response ends if no CONTINUATION frames
		}
	}

	/**
	 * Prioritize the current stream based on the given PRIORITY frame
	 * 
	 * @param priorityFrame
	 */
	private void prioritize(PriorityFrame priorityFrame) {
		prioritize(connection.getStream(priorityFrame.getParentStreamId()), priorityFrame.isExclusive(),
				priorityFrame.getWeight());
	}

	/**
	 * Prioritize the current stream in the tree.
	 * 
	 * @param priorityFrame
	 */
	private void prioritize(NettyStream parentStream) {
		prioritize(parentStream, false);
	}

	/**
	 * Prioritize the current stream in the tree.
	 * 
	 * @param priorityFrame
	 */
	private void prioritize(NettyStream parentStream, boolean exclusive) {
		prioritize(parentStream, exclusive, -1);
	}

	/**
	 * Prioritize the current stream in the tree.
	 * 
	 * @param priorityFrame
	 */
	private void prioritize(NettyStream newParentStream, boolean exclusive, int weight) {

		if (weight > 0) {
			this.weight = weight;
		}

		if (newParentStream.ancestors().contains(this)) {
			newParentStream.setParent(this.parentStream);
		}
		this.setParent(newParentStream);

		// If exclusive:
		if (exclusive) {
			List<NettyStream> siblings = new ArrayList<NettyStream>(newParentStream.getDependentStreams());
			for (NettyStream sibling : siblings) {
				if (!sibling.equals(this)) {
					sibling.setParent(this);
				}
			}
		}

	}

	private List<NettyStream> ancestors() {
		List<NettyStream> ancestors = new ArrayList<NettyStream>();
		NettyStream parent = this.parentStream;
		while (parent != null) {
			ancestors.add(parent);
			parent = parent.parentStream;
		}
		return ancestors;
	}

	private void setParent(NettyStream newParent) {
		this.parentStream.dependentStreams.remove(this);
		this.parentStream = newParent;
		if (!newParent.dependentStreams.contains(this)) {
			newParent.dependentStreams.add(this);
		}
	}

	public static void main(String[] args) {

		try (Connection dummy = new Http2FrameHandler(null, null)) {

			NettyStream x = (NettyStream) dummy.getConnectionStream();
			NettyStream a = (NettyStream) dummy.newStream();
			NettyStream b = (NettyStream) dummy.newStream();
			NettyStream c = (NettyStream) dummy.newStream();
			NettyStream d = (NettyStream) dummy.newStream();
			NettyStream e = (NettyStream) dummy.newStream();
			NettyStream f = (NettyStream) dummy.newStream();

			System.out.println(x);
			System.out.println(a);
			System.out.println(b);
			System.out.println(c);
			System.out.println(d);
			System.out.println(e);
			System.out.println(f);

			System.out.println("---------------------");
			b.prioritize(a);
			c.prioritize(a);
			d.prioritize(c);
			e.prioritize(c, true);
			f.prioritize(d);

			System.out.println(x);
			System.out.println(a);
			System.out.println(b);
			System.out.println(c);
			System.out.println(d);
			System.out.println(e);
			System.out.println(f);

			System.out.println("----------------------");
			a.prioritize(d, true);
			System.out.println(x);
			System.out.println(a);
			System.out.println(b);
			System.out.println(c);
			System.out.println(d);
			System.out.println(e);
			System.out.println(f);

		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
}
