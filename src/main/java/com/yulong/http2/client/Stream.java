package com.yulong.http2.client;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.yulong.http2.client.common.ErrorCodeRegistry;
import com.yulong.http2.client.frame.ContinuationFrame;
import com.yulong.http2.client.frame.DataFrame;
import com.yulong.http2.client.frame.FrameHistory;
import com.yulong.http2.client.frame.HeadersFrame;
import com.yulong.http2.client.frame.PriorityFrame;
import com.yulong.http2.client.frame.ResetFrame;
import com.yulong.http2.client.message.Http2Response;
import com.yulong.http2.client.message.PushRequest;

/**
 * An HTTP/2 Stream representation
 * 
 * @author yushi
 * @since Jan 11 2016
 *
 */
public interface Stream extends Closeable {

	public static enum State {
		IDLE, RESERVED_LOCAL, RESERVED_REMOTE, OPEN, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED, RESET_LOCAL, RESET_REMOTE;
	}

	/**
	 * Send a HEADERS frame on the stream
	 * 
	 * @param headersFrame
	 * @throws ConnectionException
	 */
	public void headers(HeadersFrame headersFrame) throws ConnectionException;

	/**
	 * Send a PRIORITY frame on the stream
	 * 
	 * @param priorityFrame
	 * @throws ConnectionException
	 */
	public void priority(PriorityFrame priorityFrame) throws ConnectionException;

	/**
	 * Send a CONTINUATION frame on the stream
	 * 
	 * @param continuationFrame
	 * @throws ConnectionException
	 */
	public void continuation(ContinuationFrame continuationFrame) throws ConnectionException;

	/**
	 * Send a DATA frame on the stream
	 * 
	 * @param dataFrame
	 * @throws ConnectionException
	 */
	public void data(DataFrame dataFrame) throws ConnectionException;

	/**
	 * Send a RST_STREAM frame on the stream
	 * 
	 * @param errorCode
	 */
	public void reset(ErrorCodeRegistry errorCode);

	/**
	 * Send a WINDOW_UPDATE frame with the give increment
	 * 
	 * @param windowSizeIncrement
	 * @throws ConnectionException
	 */
	public void windowUpdate(int windowSizeIncrement) throws ConnectionException;

	/**
	 * Get the parent stream
	 * 
	 * @return
	 */
	public Stream getParentStream();

	/**
	 * Get the dependent streams
	 * 
	 * @return
	 */
	public List<? extends Stream> getDependentStreams();

	/**
	 * Get the weight of the stream
	 * 
	 * @return
	 */
	public int getWeight();

	/**
	 * Get the stream id
	 * 
	 * @return
	 */
	public int getId();

	/**
	 * Get the connection on which the stream lives
	 * 
	 * @return
	 */
	public Connection getConnection();

	/**
	 * Set the current stream state to the give value
	 * 
	 * @return
	 */
	public State getState();

	/**
	 * Get a future instance of Http2Response of the stream. It's useful when
	 * you'd like to get the response of the stream
	 * 
	 * @return
	 */
	public Future<Http2Response> getResponseFuture();

	/**
	 * Get all the received frames on the stream
	 * 
	 * @return
	 */
	public List<FrameHistory> allReceivedFrames();

	/**
	 * If this stream is reset by peer, return the received RST_STREAM frame
	 * 
	 * @return
	 */
	public ResetFrame resetByPeer();

	/**
	 * Return all the promised streams from this stream.
	 * 
	 * @return
	 */
	public Map<PushRequest, Stream> promisedStreams();

	/**
	 * Add a consumer which will be called on receipt of PUSH_RPOMISE frame
	 * 
	 * @param ppl
	 */
	public void addPushRequestConsumer(Consumer<PushRequest> consumer);

}
