package com.yulong.http2.client.message;

import static com.yulong.http2.client.utils.Utils.bytes2String;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * An HTTP/2 response
 * 
 * @author yushi
 * @since Jan. 2016
 *
 */
public class Http2Response {

	private final Http2Headers headers;
	private byte[] entity;

	private final String charset;
	private final String contentEncoding;

	private boolean complete;

	/**
	 * Constructor for a response
	 * 
	 * @param headers
	 *            response headers
	 */
	public Http2Response(Http2Headers headers) {

		this.headers = headers;

		// Intialize the content-encoding:
		this.contentEncoding = getContentEncoding();

		// Initialize the charset:
		this.charset = getCharset();

	}

	/**
	 * Return the response status code
	 * 
	 * @return reponse status code
	 */
	public int statusCode() {
		return Integer.parseInt(headers.first(PseudoHeader.STATUS.value()).getValue());
	}

	/**
	 * Retrieve the response headers
	 * 
	 * @return response headers
	 */
	public Http2Headers headers() {
		return headers;
	}

	/**
	 * Set the response conent as binary data
	 * 
	 * @param entity
	 *            response content as binary
	 */
	public void setEntity(byte[] entity) {
		if ("gzip".equalsIgnoreCase(contentEncoding)) {
			byte[] buffer = new byte[1024];
			try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(entity));
					ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				int len;
				while ((len = gzis.read(buffer)) > 0) {
					out.write(buffer, 0, len);
				}
				this.entity = out.toByteArray();
			} catch (IOException e) {
				e.printStackTrace(System.out);
				this.entity = null;
			}
		} else {
			this.entity = entity;
		}
	}

	/**
	 * Retrieve the response body as string according to the Charset
	 * 
	 * @return
	 */
	public String getContent() {
		if (entity == null) {
			return "";
		}
		return bytes2String(entity, charset);
	}

	private String getContentEncoding() {
		String encoding = null;
		Header contentEncodingHeader = this.headers.first("content-encoding");
		if (contentEncodingHeader != null) {
			encoding = contentEncodingHeader.getValue();
		}
		return encoding;
	}

	private String getCharset() {
		String charset = "iso-8859-1";
		Header contentTypeHeader = headers.first("content-type");
		if (contentTypeHeader != null) {
			String contentType = contentTypeHeader.getValue().toLowerCase();
			if (contentType.contains("charset")) {
				int index = contentType.indexOf("charset=");
				charset = contentType.substring(index + 8);
			}
		}
		return charset;
	}

	public boolean isComplete() {
		return complete;
	}

	public void setComplete(boolean complete) {
		this.complete = complete;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(headers).append("\r\n\r\n");
		sb.append(getContent());
		return sb.toString();
	}

}
