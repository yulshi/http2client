package com.yulong.http2.client;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

import com.yulong.http2.client.common.ErrorCodeRegistry;
import com.yulong.http2.client.common.Http2Settings;
import com.yulong.http2.client.frame.Frame;
import com.yulong.http2.client.frame.FrameHistory;
import com.yulong.http2.client.frame.GoAwayFrame;
import com.yulong.http2.client.frame.PingFrame;
import com.yulong.http2.client.frame.SettingsFrame;
import com.yulong.http2.client.message.Http2Headers;
import com.yulong.http2.client.message.Http2Request;
import com.yulong.http2.client.message.Http2Response;

public interface Connection extends Closeable {

	public static enum StartBy {
		upgrade, alpn, prior_knowledge
	}

	/**
	 * Send an arbitrary frame
	 * 
	 * @param frame
	 *            a frame instance
	 * @throws ConnectionException
	 *             throws when a failure occurs in sending frame
	 */
	void send(Frame frame) throws ConnectionException;

	/**
	 * Get all the streams on this connection
	 * 
	 * @return all the streams on this connection
	 */
	Collection<? extends Stream> getStreams();

	/**
	 * Get the stream according to the given stream identifier
	 * 
	 * @param streamId
	 * @return
	 */
	Stream getStream(int streamId);

	/**
	 * Get the connection control stream whose stream id is 0
	 * 
	 * @return
	 */
	Stream getConnectionStream();

	/**
	 * Send a HTTP request on the connection
	 * 
	 * @param request
	 * @return A future of Http2Response
	 * @throws ConnectionException
	 */
	Future<Http2Response> request(Http2Request request) throws ConnectionException;

	/**
	 * Send a Settings frame and wait for the response Settings reply
	 * 
	 * @param settingsFrame
	 * @return The response settings frame
	 * @throws ConnectionException
	 */
	SettingsFrame settings(SettingsFrame settingsFrame) throws ConnectionException;

	/**
	 * Send a Ping frame and wait for the response Ping frame
	 * 
	 * @param pingFrame
	 * @return the response Ping frame
	 * @throws ConnectionException
	 */
	PingFrame ping(PingFrame pingFrame) throws ConnectionException;

	/**
	 * Send a GOAWAY frame
	 * 
	 * @param errorCode
	 * @param debugData
	 * @throws ConnectionException
	 */
	void goAway(ErrorCodeRegistry errorCode, String debugData) throws ConnectionException;

	/**
	 * Send a WINDOW_UPDATE frame on the connection
	 * 
	 * @param windowSizeIncrement
	 * @throws ConnectionException
	 */
	void windowUpdate(int windowSizeIncrement) throws ConnectionException;

	/**
	 * Create a new stream that is in IDEL state
	 * 
	 * @return
	 */
	Stream newStream();

	/**
	 * Return the connection's settings
	 * 
	 * @return
	 */
	Http2Settings currentSettings();

	/**
	 * Decode the given header block using the current Decoder instance
	 * 
	 * @param headerBlock
	 * @return
	 * @throws ConnectionException
	 */
	Http2Headers decode(byte[] headerBlock) throws ConnectionException;

	/**
	 * Encode the given headers using the current Encoder instance
	 * 
	 * @param headers
	 * @return
	 */
	byte[] encode(Http2Headers headers);

	/**
	 * Close all streams in the "idle" state that might have been initiated by
	 * that peer with a lower-valued stream identifier.
	 * 
	 * @param streamId
	 */
	void closeUnusedIdleStreams(int streamId);

	/**
	 * Get all the received frames on the connection (excludes those whose
	 * stream id is not 0)
	 * 
	 * @return
	 */
	List<FrameHistory> allReceivedFrames();

	/**
	 * Wait for the connection to be upgraded
	 * 
	 * @param timeout
	 * @param unit
	 * @throws Http2StartingException
	 */
	//void awaitConfirmation(long timeout, TimeUnit unit) throws Http2StartingException;

	/**
	 * If this connection has been closed by peer, return the GOAWAY frame.
	 * 
	 * @return GoAwayFrame
	 */
	GoAwayFrame closedByPeer();

}
