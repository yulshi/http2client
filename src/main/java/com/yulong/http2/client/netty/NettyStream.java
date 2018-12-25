package com.yulong.http2.client.netty;

import static io.netty.buffer.Unpooled.buffer;
import static com.yulong.http2.client.common.Constants.CONNECTION_STREAM_ID;
import static com.yulong.http2.client.utils.Debug.debugPushRequest;
import static com.yulong.http2.client.utils.Debug.debugResponseCache;
import static com.yulong.http2.client.utils.Debug.debugResponseHeader;
import static com.yulong.http2.client.utils.LogUtil.log;
import static com.yulong.http2.client.utils.Utils.combine;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import com.yulong.http2.client.Connection;
import com.yulong.http2.client.ConnectionException;
import com.yulong.http2.client.Stream;
import com.yulong.http2.client.StreamException;
import com.yulong.http2.client.common.ErrorCodeRegistry;
import com.yulong.http2.client.common.FlowControlWindow;
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
 */
public class NettyStream implements Stream {

	private final Http2FrameHandler connection;
	private final int id;
	private final StateWrapper state;

	private NettyStream parentStream;
	private final List<NettyStream> dependentStreams;
	private int weight;

	private byte[] receivedHeaderBlock;

	private ByteBuf dataBuffer = buffer(13684);

	private Http2ResponseImpl response;

	private FrameHistory lastReceivedNonControlFrame;
	private ResetFrame receivedResetFrame;

	private final ExecutorService executorService;

	private Stream currentPromisedStream;
	private PushPromiseFrame currentPushPromiseFrame;
	private final Map<PushRequest, Stream> promisedStreams;
	private final List<Consumer<PushRequest>> pushRequestConsumers;
	private final List<Consumer<DataFrame>> dataFrameConsumers;
	private final FlowControlWindow window;

	private Path cacheFile;
	private OutputStream cacheOutputStream;
	private final int cacheThreshold = Integer.getInteger("http2.response.cache.threshold", 65535);

	NettyStream(Http2FrameHandler connection, int id) {
		this(connection, id, State.IDLE);
	}

	NettyStream(Http2FrameHandler connection, int id, State state) {

		// Coalition:
		this.connection = connection;
		this.id = id;
		this.state = new StateWrapper(state);

		// Dependencies and priority:
		if (id == CONNECTION_STREAM_ID) {
			this.parentStream = null;
		} else {
			this.parentStream = connection.getConnectionStream();
			//this.parentStream.dependentStreams.add(this);
		}
		this.dependentStreams = new ArrayList<NettyStream>();
		this.weight = 16;

		// Responses:
		this.receivedHeaderBlock = new byte[0];

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
		dataFrameConsumers = new ArrayList<>();
		window = new FlowControlWindow(id, this.connection.currentSettings().getInitialWindowSize());

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

		int length = dataFrame.getPayloadLength();

		if (dataFrame.isPadded()) {

			Math.min(window.availableSize(length), connection.getWindow().availableSize(length));
			window.consume(length);
			connection.getWindow().consume(length);
			connection.send(dataFrame);

		} else {

			byte[] data = dataFrame.getData();
			boolean endStreamFlag = dataFrame.isEndStream();

			int sizeAvailable = Math.min(window.availableSize(1), connection.getWindow().availableSize(1));

			if (sizeAvailable >= length) {

				window.consume(length);
				connection.getWindow().consume(length);
				connection.send(dataFrame);

			} else {

				window.consume(sizeAvailable);
				connection.getWindow().consume(sizeAvailable);

				// fragment the data frame:
				connection.send(new DataFrame(getId(), false, Arrays.copyOfRange(data, 0, sizeAvailable)));

				data(new DataFrame(getId(), endStreamFlag, Arrays.copyOfRange(data, sizeAvailable, length)));

			}

		}

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
		// log("---------- releasing resources occupied by the current stream: " + id + " ----------");
		executorService.shutdown();
		drain();
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
	 * Get the response of the stream in the blocking way.
	 * 
	 * @return
	 * @throws TimeoutException
	 */
	public Http2Response getResponse() throws ConnectionException {

		int idleTimeoutSeconds = Integer.getInteger("http2.response.timeout", 300);
		// log("Start waiting for the response (idle timeout in seconds: " + idleTimeoutSeconds + ") ...");

		String failReason = null;
		long timeElapsed = -1;

		long start = System.currentTimeMillis();

		while (response == null || !response.isComplete()) {

			// Check frame history to see if the idle time of the stream is larger than 300 seconds,
			// if so, stop waiting:
			if (lastReceivedNonControlFrame != null) {
				timeElapsed = System.currentTimeMillis() - lastReceivedNonControlFrame.getTimestamp();
				if (timeElapsed > idleTimeoutSeconds * 1000) {
					failReason = "Failed to get response after " + timeElapsed
							+ " milliseconds, the last received non-control frame is " + lastReceivedNonControlFrame;
					break;
				}
			} else {
				timeElapsed = System.currentTimeMillis() - start;
				if (timeElapsed > idleTimeoutSeconds * 1000) {
					failReason = "Failed to get response after " + timeElapsed
							+ " milliseconds, no any non-control frame were received";
					break;
				}
			}

			// If a RST_STREAM frame is received, stop waiting immediately:
			if (receivedResetFrame != null) {
				failReason = "Failed to get response because RST_STREAM is received";
				break;
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}

		}

		//return responseComplete ? response : null;
		if (failReason == null) {
			return response;
		} else {
			throw new ConnectionException(ErrorCodeRegistry.UNKNOWN, failReason);
		}

	}

	/**
	 * If this stream is reset by peer, return the received RST_STREAM frame
	 * 
	 * @return
	 */
	@Override
	public ResetFrame resetByPeer() {
		return receivedResetFrame;
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
	 * Add a consumer which will be called on receipt of DATA frame
	 * 
	 * @param dataFrame
	 */
	@Override
	public void addDataFrameConsumer(Consumer<DataFrame> dataFrameConsumer) {
		dataFrameConsumers.add(dataFrameConsumer);
	}

	/**
	 * Add the default DataFrame Consumer that will send back WINDOW_UPDATE frame
	 */
	@Override
	public void addDefaultDataFrameConsumer() {
		addDataFrameConsumer(dataFrame -> {
			int dataLength = dataFrame.getPayloadLength();
			if (dataLength > 0) {
				try {
					this.windowUpdate(dataLength);
					this.getConnection().windowUpdate(dataLength);
				} catch (ConnectionException e) {
					log("Failed to send WINDOW_UPDATE frame: " + e);
				}
			}
		});
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

		dataBuffer.writeBytes(dataFrame.getData());

		int receivedDataLength = dataBuffer.readableBytes();

		if (receivedDataLength > cacheThreshold) {
			if (cacheFile == null) {
				// create cache file if required:
				try {
					String tempDir = System.getProperty("http2.response.cache.dir");
					if (tempDir == null) {
						tempDir = System.getProperty("java.io.tmpdir");
					}
					String prefix = "http2-cache-" + this.getConnection().hashCode() + "-";
					String suffix = "-" + this.getId();
					cacheFile = Files.createTempFile(Paths.get(tempDir), prefix, suffix);
					cacheOutputStream = Files.newOutputStream(cacheFile);
				} catch (IOException e) {
					System.out.println("Unable to create cache file: " + e);
					cacheFile = null;
				}
			} else {
				// cache content to file if required:
				debugResponseCache(() -> "Starting to cache content to file: " + receivedDataLength);
				try {
					dataBuffer.readBytes(cacheOutputStream, receivedDataLength);
					dataBuffer.clear();
					response.cacheFile(cacheFile);
				} catch (IOException e) {
					System.out.println("Unable to cache DATA to cache file (" + cacheFile + ") due to " + e);
					cacheFile = null; // disable cache
					if (cacheOutputStream != null) {
						try {
							cacheOutputStream.close();
						} catch (IOException ioe) {
							log("Error closing cache file output stream: " + ioe);
						}
					}
				}
			}
		}

		for (Consumer<DataFrame> dataFrameConsumer : dataFrameConsumers) {
			dataFrameConsumer.accept(dataFrame);
		}

		if (dataFrame.isEndStream()) {
			endStreamRemotely();
			debugResponseCache(() -> "data not cached: " + receivedDataLength);
			writeResponseEntity();
			response.setComplete(true);
			if (cacheOutputStream != null) {
				try {
					cacheOutputStream.close();
				} catch (IOException ioe) {
					log("Error closing cache file output stream: " + ioe);
				}
			}
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

		int increment = windowUpdateFrame.getWindowSizeIncrement();
		if (increment <= 0) {
			throw new StreamException(id, ErrorCodeRegistry.PROTOCOL_ERROR,
					"The flow-control window increment should not be 0 or negative: "
							+ windowUpdateFrame.getWindowSizeIncrement());
		}

		if (!window.increment(increment)) {
			reset(ErrorCodeRegistry.FLOW_CONTROL_ERROR);
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
		this.receivedResetFrame = resetFrame;
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
		switch (frame.getType()) {
		case HEADERS:
		case CONTINUATION:
		case DATA:
		case PUSH_PROMISE:
			lastReceivedNonControlFrame = new FrameHistory(frame);
			break;
		default:
			break;
		}
	}

	/**
	 * expose the window to connection
	 * 
	 * @return
	 */
	FlowControlWindow getWindow() {
		return window;
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

		debugResponseHeader(headers);

		if (response == null) {
			// Headers:
			response = new Http2ResponseImpl(id, headers);
		} else {
			// Trailers:
			writeResponseEntity();
			response.trailers(headers);
		}

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

		debugPushRequest(pushRequest);

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

	private void writeResponseEntity() {
		byte[] tmp = new byte[dataBuffer.readableBytes()];
		dataBuffer.readBytes(tmp);
		response.entity(tmp);
	}

	private void drain() {
		if (connection.config().isDrainStreamOnClose()) {
			//log("removed stream: " + this);
			connection.streams().remove(id);
			this.currentPromisedStream = null;
			this.currentPushPromiseFrame = null;
			this.dataBuffer = null;
			this.receivedHeaderBlock = null;
			this.receivedResetFrame = null;
		}
	}

	private static class StateWrapper {

		private State state;

		public StateWrapper(State state) {
			this.state = state;
		}

		public synchronized boolean compareAndSet(State expect, State update) {

			if (expect == state) {
				set(update);
				return true;
			} else {
				return false;
			}

		}

		public State get() {
			return state;
		}

		public void set(State state) {
			this.state = state;
		}

	}

}
