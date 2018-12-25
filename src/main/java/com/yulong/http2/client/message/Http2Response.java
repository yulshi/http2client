package com.yulong.http2.client.message;

import java.io.InputStream;
import java.util.Map;

/**
 * An HTTP/2 response
 */
public interface Http2Response {

	public int statusCode();

	public String contentType();

	public int contentLength();

	public String jsessionId();

	public String charset();

	public Http2Headers headers();

	public byte[] entity();

	public InputStream entityAsInputStream();

	public Http2Headers trailers();

	public Map<PushRequest, Http2Response> pushedResponses();

	/**
	 * Retrieve the response body as string according to the Charset
	 * 
	 * @return
	 */
	public String getContent();

}
