package com.yulong.http2.client.netty;

import static com.yulong.http2.client.common.Constants.CONNECTION_STREAM_ID;
import static com.yulong.http2.client.utils.LogUtil.log;
import static com.yulong.http2.client.utils.Utils.string2Bytes;
import static com.yulong.http2.client.utils.Utils.toHexString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import com.twitter.hpack.HeaderListener;
import com.yulong.http2.client.Connection;
import com.yulong.http2.client.ConnectionException;
import com.yulong.http2.client.Http2StartingException;
import com.yulong.http2.client.Stream;
import com.yulong.http2.client.StreamException;
import com.yulong.http2.client.Stream.State;
import com.yulong.http2.client.common.ErrorCodeRegistry;
import com.yulong.http2.client.common.Http2Settings;
import com.yulong.http2.client.frame.Continuable;
import com.yulong.http2.client.frame.ContinuationFrame;
import com.yulong.http2.client.frame.DataFrame;
import com.yulong.http2.client.frame.Frame;
import com.yulong.http2.client.frame.FrameHistory;
import com.yulong.http2.client.frame.GoAwayFrame;
import com.yulong.http2.client.frame.HeadersFrame;
import com.yulong.http2.client.frame.Padded;
import com.yulong.http2.client.frame.PingFrame;
import com.yulong.http2.client.frame.PriorityFrame;
import com.yulong.http2.client.frame.PushPromiseFrame;
import com.yulong.http2.client.frame.ResetFrame;
import com.yulong.http2.client.frame.SettingsFrame;
import com.yulong.http2.client.frame.WindowUpdateFrame;
import com.yulong.http2.client.message.Header;
import com.yulong.http2.client.message.Http2Headers;
import com.yulong.http2.client.message.Http2Request;
import com.yulong.http2.client.message.Http2Response;
import com.yulong.http2.client.utils.Utils;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * The main class to process the received Frame objects and wrap them into more
 * specific frame types.
 * 
 * @author yushi
 *
 */
public class Http2FrameHandler extends ChannelInboundHandlerAdapter implements Connection {

	private ChannelHandlerContext ctx;
	private final byte[] connectionPreface;

	private ConcurrentHashMap<Integer, NettyStream> streams = new ConcurrentHashMap<>();
	private final AtomicInteger localCurrentStreamId = new AtomicInteger(3);
	private final AtomicInteger lastReceivedStreamId = new AtomicInteger();
	private final AtomicReference<GoAwayFrame> lastReceivedGoAwayFrame = new AtomicReference<>(null);
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private final ChannelPromise http2InUsePromise;

	private ChannelPromise settingsPromise;
	private SettingsFrame lastResponseSettingsFrame;

	private ChannelPromise pingPromise;
	private PingFrame lastResponsePingFrame;

	private Frame lastReceivedFrame = null;
	private Frame currentReceivedFrame = null;
	private final List<FrameHistory> allReceivedFrames;

	// The settings received fromserver
	private final Http2Settings settingsRequiredByRemote = new Http2Settings();
	// The settings sent to server
	private final Http2Settings settingsRequiredByLocal = new Http2Settings();

	private Decoder decoder;
	private Encoder encoder;

	private final NettyStream connectionStream;

	private boolean sendAcknowledgePrefaceImmediately;

	public Http2FrameHandler(ChannelPromise http2InUsePromise, byte[] connectionPreface) {
		this.http2InUsePromise = http2InUsePromise;
		this.connectionPreface = connectionPreface;
		generateDecoder();
		generateEncoder();
		this.allReceivedFrames = new ArrayList<>();
		this.connectionStream = new NettyStream(this, CONNECTION_STREAM_ID, State.OPEN);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		this.ctx = ctx;
		if (connectionPreface != null) {
			log("Sending connection preface: " + toHexString(connectionPreface));
			this.ctx.writeAndFlush(Unpooled.copiedBuffer(connectionPreface));
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		this.closed.compareAndSet(false, true);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

		log("Exception occured during reading frames: " + cause);
		cause.printStackTrace(System.out);

		if (cause instanceof StreamException) {
			// Will not close connection in most cases:
			StreamException ex = (StreamException) cause;
			Stream errorStream = getStream(ex.getStreamId());
			errorStream.reset(ex.GetError());
		} else if (cause instanceof ConnectionException) {
			ConnectionException ex = (ConnectionException) cause;
			goAway(ex.GetError(), ex.getMessage());
		} else {
			goAway(ErrorCodeRegistry.UNKNOWN, "unexpected exception");
		}

	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws ConnectionException {

		Frame rawFrame = (Frame) msg;

		//		if (rawFrame.getPayloadLength() > settingsRequiredByLocal.getMaxFrameSize()) {
		//			throw new ConnectionException(ErrorCodeRegistry.FRAME_SIZE_ERROR,
		//					"The frame payload length(" + rawFrame.getPayloadLength()
		//							+ ") exceeds the size defined in SETTINGS_MAX_FRAME_SIZE(" + settingsRequiredByLocal.getMaxFrameSize()
		//							+ ")");
		//		}

		lastReceivedStreamId.set(rawFrame.getStreamId());

		switch (rawFrame.getType()) {
		case DATA:
			preprocess(new DataFrame(rawFrame));
			processDataFrame();
			break;
		case HEADERS:
			preprocess(new HeadersFrame(rawFrame));
			processHeadersFrame();
			break;
		case PRIORITY:
			preprocess(new PriorityFrame(rawFrame));
			processPriorityFrame();
			break;
		case RST_STREAM:
			preprocess(new ResetFrame(rawFrame));
			processResetFrame();
			break;
		case SETTINGS:
			preprocess(new SettingsFrame(rawFrame));
			processSettingsFrame();
			break;
		case PUSH_PROMISE:
			preprocess(new PushPromiseFrame(rawFrame));
			processPushPromise();
			break;
		case PING:
			preprocess(new PingFrame(rawFrame));
			processPingFrame();
			break;
		case GO_AWAY:
			preprocess(new GoAwayFrame(rawFrame));
			processGoAwayFrame();
			break;
		case WINDOW_UPDATE:
			preprocess(new WindowUpdateFrame(rawFrame));
			processWindowUpdate();
			break;
		case CONTINUATION:
			preprocess(new ContinuationFrame(rawFrame));
			processContinuationFrame();
			break;
		default:
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR, "Unkown frame: " + rawFrame);
		}

	}

	/**
	 * Send an arbitrary frame
	 * 
	 * @param frame
	 *            a frame instance
	 * @throws ConnectionException
	 *             throws when a failure occurs in sending frame
	 */
	@Override
	public void send(Frame frame) throws ConnectionException {

		log(">> " + frame);

		if (frame instanceof PingFrame) {
			this.pingPromise = ctx.newPromise();
		}

		if (frame instanceof SettingsFrame) {
			this.settingsPromise = ctx.newPromise();
		}

		ChannelFuture f = ctx.writeAndFlush(Unpooled.copiedBuffer(frame.asBytes()));
		if (!f.awaitUninterruptibly(1L, TimeUnit.SECONDS)) {
			throw new ConnectionException(ErrorCodeRegistry.UNKNOWN, "Timed out waiting for sending frame");
		}
		if (!f.isSuccess()) {
			throw new ConnectionException(ErrorCodeRegistry.UNKNOWN, "Failed to send frame", f.cause());
		}

	}

	/**
	 * Get all the streams on this connection
	 * 
	 * @return all the streams on this connection
	 */
	@Override
	public Collection<NettyStream> getStreams() {
		return Collections.unmodifiableCollection(streams.values());
	}

	/**
	 * Get the stream according to the given stream identifier
	 * 
	 * @param streamId
	 * @return
	 */
	@Override
	public NettyStream getStream(int streamId) {
		if (streamId == CONNECTION_STREAM_ID) {
			return getConnectionStream();
		}
		NettyStream stream = streams.get(streamId);
		if (stream == null) {
			if (streamId < localCurrentStreamId.get()) {
				stream = new NettyStream(this, streamId, State.CLOSED);
			} else {
				stream = (NettyStream) newStream();
			}
		}
		return stream;
	}

	/**
	 * Get the connection control stream whose stream id is 0
	 * 
	 * @return
	 */
	@Override
	public NettyStream getConnectionStream() {
		return connectionStream;
	}

	/**
	 * Send a HTTP request on the connection
	 * 
	 * @param request
	 * @return A future of Http2Response
	 * @throws ConnectionException
	 */
	@Override
	public Future<Http2Response> request(Http2Request request) throws ConnectionException {

		final Stream stream = newStream();

		boolean endStreamFlag = true;
		if (request.getMethod().equalsIgnoreCase("POST") || request.getMethod().equalsIgnoreCase("PUT")) {
			endStreamFlag = false;
		}

		byte[] headerBlock = encode(request.headers());

		int maxFragmentSize = 10;

		List<byte[]> fragments = Utils.fragment(headerBlock, maxFragmentSize);
		if (fragments.size() > 1) {
			HeadersFrame headersFrame = new HeadersFrame(stream.getId(), endStreamFlag, false, fragments.get(0));
			stream.headers(headersFrame);
			for (int i = 1; i < fragments.size() - 1; i++) {
				ContinuationFrame cf = new ContinuationFrame(stream.getId(), false, fragments.get(i));
				stream.continuation(cf);
			}
			ContinuationFrame cf = new ContinuationFrame(stream.getId(), true, fragments.get(fragments.size() - 1));
			stream.continuation(cf);
		} else {
			HeadersFrame headersFrame = new HeadersFrame(stream.getId(), endStreamFlag, true, headerBlock);
			stream.headers(headersFrame);
		}

		return stream.getResponseFuture();

	}

	/**
	 * Send a Settings frame and return the response Settings reply
	 * 
	 * @param settingsFrame
	 * @return The response settings frame
	 * @throws ConnectionException
	 */
	@Override
	public SettingsFrame settings(SettingsFrame settingsFrame) throws ConnectionException {
		send(settingsFrame);
		if (!this.settingsPromise.awaitUninterruptibly(2L, TimeUnit.SECONDS)) {
			GoAwayFrame goAwayFrame = this.lastReceivedGoAwayFrame.get();
			if (goAwayFrame != null) {
				throw new ConnectionException(goAwayFrame.getErrorCode(), goAwayFrame.getDebugData());
			}
			throw new ConnectionException(ErrorCodeRegistry.SETTINGS_TIMEOUT, "Timed out waiting for settings");
		}
		// The acknowledged SETTINGS frame has been received and we need to set
		// the settings:
		settingsRequiredByLocal.setFrom(settingsFrame);
		generateEncoder();
		return this.lastResponseSettingsFrame;
	}

	/**
	 * Send a Ping frame and return the response Ping frame
	 * 
	 * @param pingFrame
	 * @return the response Ping frame
	 * @throws ConnectionException
	 */
	@Override
	public PingFrame ping(PingFrame pingFrame) throws ConnectionException {
		send(pingFrame);
		if (!this.pingPromise.awaitUninterruptibly(2L, TimeUnit.SECONDS)) {
			GoAwayFrame goAwayFrame = this.lastReceivedGoAwayFrame.get();
			if (goAwayFrame != null) {
				throw new ConnectionException(goAwayFrame.getErrorCode(), goAwayFrame.getDebugData());
			}
			throw new ConnectionException(ErrorCodeRegistry.PING_TIMEOUT, "Timed out waiting for ping response");
		}
		return this.lastResponsePingFrame;
	}

	/**
	 * Send a GOAWAY frame
	 * 
	 * @param errorCode
	 * @param debugData
	 * @throws ConnectionException
	 */
	@Override
	public void goAway(ErrorCodeRegistry errorCode, String debugData) {
		if (!closed.get()) {
			GoAwayFrame goAwayFrame = new GoAwayFrame(lastReceivedStreamId.get(), errorCode, debugData);
			try {
				send(goAwayFrame);
			} catch (ConnectionException e) {
				log(e);
			}
			disconnect();
			closed.compareAndSet(false, true);
		}
	}

	/**
	 * Send a WINDOW_UPDATE frame on the connection
	 * 
	 * @param windowSizeIncrement
	 * @throws ConnectionException
	 */
	@Override
	public void windowUpdate(int windowSizeIncrement) throws ConnectionException {
		send(new WindowUpdateFrame(CONNECTION_STREAM_ID, windowSizeIncrement));
	}

	/**
	 * Create a new stream that is in IDEL state
	 * 
	 * @return
	 */
	@Override
	public NettyStream newStream() {
		NettyStream stream = new NettyStream(this, localCurrentStreamId.getAndAdd(2));
		if (streams.putIfAbsent(stream.getId(), stream) == null) {
			// This is a new stream:
			stream.setState(State.IDLE);
		}
		return stream;
	}

	/**
	 * Return the connection's settings
	 * 
	 * @return
	 */
	@Override
	public Http2Settings currentSettings() {
		return this.settingsRequiredByRemote;
	}

	/**
	 * Decode the given header block using the current Decoder instance
	 * 
	 * @param headerBlock
	 * @return
	 * @throws ConnectionException
	 */
	@Override
	public Http2Headers decode(byte[] headerBlock) throws ConnectionException {

		try (ByteArrayInputStream in = new ByteArrayInputStream(headerBlock)) {
			final Http2Headers headers = new Http2Headers();

			decoder.decode(in, new HeaderListener() {
				@Override
				public void addHeader(byte[] key, byte[] value, boolean sensitive) {
					headers.add(Utils.bytes2String(key), Utils.bytes2String(value));
				}
			});

			decoder.endHeaderBlock();

			return headers;
		} catch (IOException e) {
			throw new ConnectionException(ErrorCodeRegistry.COMPRESSION_ERROR, e.getMessage(), e);
		}

	}

	/**
	 * Encode the given headers using the current Encoder instance
	 * 
	 * @param headers
	 * @return
	 */
	@Override
	public byte[] encode(Http2Headers headers) {

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (Header header : headers.all()) {
				String headerName = header.getName().toLowerCase();
				boolean sensitive = headerName.matches("(cookie|set-cookie|sensitive-).*");
				encoder.encodeHeader(out, string2Bytes(headerName), string2Bytes(header.getValue()), sensitive);
			}
			return out.toByteArray();
		} catch (IOException e) {
			return null;
		}

	}

	/**
	 * Close the connection gracefully
	 */
	@Override
	public void close() {
		if (this.ctx != null) {
			goAway(ErrorCodeRegistry.NO_ERROR, "gracefully shutdown");
		}
	}

	/**
	 * Close all streams in the "idle" state that might have been initiated by
	 * that peer with a lower-valued stream identifier.
	 * 
	 * @param streamId
	 */
	public void closeUnusedIdleStreams(int streamId) {
		for (NettyStream stream : this.streams.values()) {
			if (stream.getId() < streamId && stream.getState() == State.IDLE) {
				stream.setState(State.CLOSED);
			}
		}
	}

	/**
	 * Get all the received frames on the connection (excludes those whose
	 * stream id is not 0)
	 * 
	 * @return
	 */
	@Override
	public List<FrameHistory> allReceivedFrames() {
		return Collections.unmodifiableList(allReceivedFrames);
	}

	/**
	 * If this connection has been closed by peer, return the GOAWAY frame.
	 * 
	 * @return GoAwayFrame
	 */
	@Override
	public GoAwayFrame closedByPeer() {
		return lastReceivedGoAwayFrame.get();
	}

	/**
	 * Wait for the connection to be upgraded
	 * 
	 * @param timeout
	 * @param unit
	 * @throws Http2StartingException
	 */
	public void awaitConfirmation(long timeout, TimeUnit unit) throws Http2StartingException {
		if (!this.http2InUsePromise.awaitUninterruptibly(timeout, unit)) {
			throw new Http2StartingException("Timed out waiting for the reply of connection preface");
		}
		if (!this.http2InUsePromise.isSuccess()) {
			throw (Http2StartingException) this.http2InUsePromise.cause();
		}
	}

	public void setSendAcknowledgePrefaceImmediately(boolean sendAcknowledgePrefaceImmediately) {
		this.sendAcknowledgePrefaceImmediately = sendAcknowledgePrefaceImmediately;
	}

	/**
	 * Pre-process the received frame
	 * 
	 * @param frame
	 */
	private void preprocess(Frame frame) {

		log("<< " + frame);

		if (currentReceivedFrame == null) {
			lastReceivedFrame = currentReceivedFrame = frame;
		} else {
			lastReceivedFrame = currentReceivedFrame;
			currentReceivedFrame = frame;
		}

		allReceivedFrames.add(new FrameHistory(currentReceivedFrame));
		int streamId = currentReceivedFrame.getStreamId();
		if (streamId != 0) {
			NettyStream stream = (NettyStream) getStream(streamId);
			stream.addReceivedFrame(currentReceivedFrame);
		}

	}

	/**
	 * Process the received SettingsFrame
	 */
	private void processSettingsFrame() throws ConnectionException {

		SettingsFrame settingsFrame = (SettingsFrame) currentReceivedFrame;

		if (settingsFrame.isAck()) {
			validatePayloadLength(0);
			// If it's a reply, get the promise done with success:
			if (!this.http2InUsePromise.isDone()) {
				// it should be a connection preface:
				this.http2InUsePromise.setSuccess();
				log("================= HTTP/2 protocol is in use now =================");
			} else {
				// it should be a normal reply:
				this.lastResponseSettingsFrame = settingsFrame;
				this.settingsPromise.setSuccess();
			}
		} else {
			if (settingsFrame.getPayloadLength() % 6 != 0) {
				throw new ConnectionException(ErrorCodeRegistry.FRAME_SIZE_ERROR,
						"The payload length of SETTINGS frame is not a multiple of 6 octets");
			}
			// If it's not a reply, send back a reply:
			settingsRequiredByRemote.setFrom(settingsFrame);
			// the connection state is changed:
			generateDecoder();

			if (sendAcknowledgePrefaceImmediately) {
				send(SettingsFrame.REPLY);
			}

		}
	}

	/**
	 * Process the received PingFrame
	 */
	private void processPingFrame() throws ConnectionException {

		PingFrame pingFrame = (PingFrame) currentReceivedFrame;

		validatePayloadLength(PingFrame.PAYLOAD_LENGTH);

		if (pingFrame.isAck()) {
			// It's a reponse Ping, mark the ping round-trip is done
			// successfully.
			this.lastResponsePingFrame = pingFrame;
			this.pingPromise.setSuccess();
		} else {
			// It's a request Ping, so send back a response Ping with the
			// identical payload:
			send(new PingFrame(true, pingFrame.getPayload()));
		}

	}

	/**
	 * Persist the last received GOAWAY frame for later use
	 */
	private void processGoAwayFrame() {
		GoAwayFrame goAwayFrame = (GoAwayFrame) currentReceivedFrame;
		this.lastReceivedGoAwayFrame.set(goAwayFrame);
	}

	/**
	 * Validate the received HEADERS Frame and then delegate it to stream
	 */
	private void processHeadersFrame() throws ConnectionException {

		HeadersFrame headersFrame = (HeadersFrame) currentReceivedFrame;

		// For Upgrade scenario, if the stream id is 1, it's specially handled.
		if (headersFrame.getStreamId() == 1) {
			NettyStream stream = (NettyStream) getStream(1);
			stream.setState(State.HALF_CLOSED_LOCAL);
			this.streams.putIfAbsent(1, stream);
		}

		int streamId = headersFrame.getStreamId();
		NettyStream stream = streams.get(streamId);

		validateStreamId(false);
		validatePadLength(headersFrame);

		if (stream == null) {
			if (streamId == 1) {
				stream = newStream();
				stream.setState(State.HALF_CLOSED_LOCAL);
				streams.putIfAbsent(streamId, stream);
			} else {
				throw new ConnectionException(ErrorCodeRegistry.UNKNOWN,
						"A frame on a unknown stream is received: " + headersFrame.getStreamId());
			}
		}

		if (headersFrame.isPriority()) {
			stream.onPriority(headersFrame.getPriorityFrame());
		}

		stream.onHeaders(headersFrame);

	}

	/**
	 * Validate the received CONTINUATION frame and delege it to stream for
	 * furture process
	 * 
	 * @throws ConnectionException
	 */
	private void processContinuationFrame() throws ConnectionException {

		ContinuationFrame continuationFrame = (ContinuationFrame) currentReceivedFrame;

		validateStreamId(false);

		// The stream ID must be identical to the previous frame:
		if (continuationFrame.getStreamId() != lastReceivedFrame.getStreamId()) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR,
					"A CONTINUATION frame on a differnt stream is received, previous stream: " + lastReceivedFrame.getStreamId()
							+ ", current stream: " + continuationFrame.getStreamId());
		}

		// It must be preceded by a HEADERS, PUSH_PROMISE or CONTINUATION frame
		if (!(lastReceivedFrame instanceof Continuable)) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR,
					"Preceded by a frame that is not HEADERS, PUSH_PROMISE or CONTINUATION");
		}

		Continuable frame = (Continuable) lastReceivedFrame;
		if (frame.isEndHeaders()) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR,
					"Preceded by a frame with the END_HEADERS flag set");
		}

		NettyStream stream = streams.get(continuationFrame.getStreamId());
		stream.onContinuation(continuationFrame);

	}

	/**
	 * Validate the received DATA frame and then delegate to stream to furture
	 * process
	 * 
	 * @throws ConnectionException
	 */
	private void processDataFrame() throws ConnectionException {

		DataFrame dataFrame = (DataFrame) currentReceivedFrame;

		validateStreamId(false);
		validatePadLength(dataFrame);

		NettyStream stream = streams.get(dataFrame.getStreamId());

		stream.onData(dataFrame);

	}

	/**
	 * Validate the received PRIORITY frame and delegate to stream for further
	 * process
	 * 
	 * @throws ConnectionException
	 */
	private void processPriorityFrame() throws ConnectionException {

		PriorityFrame priorityFrame = (PriorityFrame) currentReceivedFrame;

		validateStreamId(false);
		validatePayloadLength(PriorityFrame.PAYLOAD_LENGTH);

		NettyStream stream = streams.get(priorityFrame.getStreamId());
		stream.onPriority(priorityFrame);

	}

	/**
	 * Validate the received PUSH_PROMISE frame and delegate to stream for
	 * furture process
	 * 
	 * @throws ConnectionException
	 */
	private void processPushPromise() throws ConnectionException {

		if (!currentSettings().isEnablePush()) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR,
					"PUSHI_PROMISE frame received on a push-disabled connection");
		}

		PushPromiseFrame pushPromiseFrame = (PushPromiseFrame) currentReceivedFrame;

		validateStreamId(false);

		// TODO we can choose to reject promised streams by returnning a
		// RST_STREAM referencing the promised stream id

		int promisedStreamId = pushPromiseFrame.getPromisedStreamId();

		NettyStream promisedStream = new NettyStream(this, promisedStreamId);
		if (streams.putIfAbsent(promisedStreamId, promisedStream) == null) {
			promisedStream.setState(State.RESERVED_REMOTE);
		} else {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR, "The promised stream ID is not in 'IDLE' state");
		}

		NettyStream stream = streams.get(pushPromiseFrame.getStreamId());
		State streamState = stream.getState();
		if (streamState != State.OPEN && streamState != State.HALF_CLOSED_LOCAL) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR,
					"illegal stream state receiving PUSHI_PROMISE frame: " + streamState);
		}

		stream.onPushPromise(pushPromiseFrame, promisedStream);

	}

	/**
	 * If the received WINDOW_UPDATE frame is to the entire connection, process
	 * it. Otherwise, delegate to the stream to furture process.
	 * 
	 * @throws ConnectionException
	 */
	private void processWindowUpdate() throws ConnectionException {

		WindowUpdateFrame windowUpdateFrame = (WindowUpdateFrame) currentReceivedFrame;

		// TODO validation on WINDOW_UPDATE frame
		validatePayloadLength(WindowUpdateFrame.PAYLOAD_LENGTH);

		int streamId = windowUpdateFrame.getStreamId();
		if (streamId == 0) {
			// To the entire connection:
			if (windowUpdateFrame.getWindowSizeIncrement() <= 0) {
				throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR,
						"The flow-control window increment should not be 0 or negative: "
								+ windowUpdateFrame.getWindowSizeIncrement());
			}
			// TODO process WINDOW_UPDATE frame on the connection
		} else {
			// To a specific stream:
			NettyStream stream = streams.get(streamId);
			stream.onWindowUpdate(windowUpdateFrame);
		}

	}

	/**
	 * When a RST_STREAM frame is received, delegate it to the stream
	 * 
	 * @throws ConnectionException
	 */
	private void processResetFrame() throws ConnectionException {

		ResetFrame resetFrame = (ResetFrame) currentReceivedFrame;
		validateStreamId(false);
		validatePayloadLength(ResetFrame.PAYLOAD_LENGTH);

		NettyStream stream = streams.get(resetFrame.getStreamId());

		// RST_STREAM frames MUST NOT be sent for a stream in the "idle" state.
		// If a RST_STREAM frame identifying an idle stream is received, the
		// recipient MUST treat this as a connection error (Section 5.4.1) of
		// type PROTOCOL_ERROR.
		if (stream.getState() == State.IDLE) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR,
					"RST_STREAM frames MUST NOT be sent for a stream in the 'idle' state");
		}

		stream.onReset(resetFrame);
	}

	private void validateStreamId(boolean connectionStream) throws ConnectionException {
		int streamId = currentReceivedFrame.getStreamId();
		if (connectionStream) {
			if (streamId != 0)
				throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR, "invalid stream id: " + streamId);
		} else {
			if (streamId == 0)
				throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR, "invalid stream id: " + streamId);
		}
	}

	private void validatePadLength(Padded paddedFrame) throws ConnectionException {
		if (paddedFrame.getPadLength() >= paddedFrame.getPayloadLength()) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR, "padding length exceeds payload length");
		}
	}

	/**
	 * The utility to validate the payload length
	 * 
	 * @param expectedLength
	 * @throws ConnectionException
	 */
	private void validatePayloadLength(int expectedLength) throws ConnectionException {
		if (currentReceivedFrame.getPayloadLength() != currentReceivedFrame.getPayload().length) {
			throw new ConnectionException(ErrorCodeRegistry.FRAME_SIZE_ERROR,
					"The Payload Length filed dose not equal to real payload size, Payload Length="
							+ currentReceivedFrame.getPayloadLength() + ", Payload size=" + currentReceivedFrame.getPayload().length);
		}
		if (currentReceivedFrame.getPayloadLength() != expectedLength) {
			throw new ConnectionException(ErrorCodeRegistry.FRAME_SIZE_ERROR,
					String.format("it's expected %d, but actually %d", expectedLength, currentReceivedFrame.getPayloadLength()));
		}
	}

	private void disconnect() {
		log("Disconnecting ...");
		ctx.close();
	}

	private void generateEncoder() {
		encoder = new Encoder(settingsRequiredByLocal.getHeaderTableSize());
	}

	private void generateDecoder() {
		decoder = new Decoder(settingsRequiredByRemote.getMaxHeaderListSize(),
				settingsRequiredByRemote.getHeaderTableSize());
	}

}
