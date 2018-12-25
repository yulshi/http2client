package com.yulong.http2.client.netty;

import static com.yulong.http2.client.utils.Debug.debugResponse;
import static com.yulong.http2.client.utils.LogUtil.log;
import static com.yulong.http2.client.utils.Utils.bytes2String;
import static com.yulong.http2.client.utils.Utils.combine;
import static com.yulong.http2.client.utils.Utils.showPartOfTextIfTooLong;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.yulong.http2.client.message.Header;
import com.yulong.http2.client.message.Http2Headers;
import com.yulong.http2.client.message.Http2Response;
import com.yulong.http2.client.message.PseudoHeader;
import com.yulong.http2.client.message.PushRequest;

public class Http2ResponseImpl implements Http2Response {

	private final int streamId;
	private final Http2Headers headers;
	private byte[] entity;
	private Http2Headers trailers;
	private final Map<PushRequest, Http2Response> pushedResponses;

	private final int statusCode;
	private final String contentEncoding;
	private final String contentType;
	private final String charset;
	private final int contentLength;
	private final String jsessionId;

	private boolean complete;
	private Path cacheFile;

	/**
	 * Constructor for a response
	 * 
	 * @param headers
	 *            response headers
	 */
	Http2ResponseImpl(int streamId, Http2Headers headers) {

		this.streamId = streamId;
		this.headers = headers;
		this.pushedResponses = new HashMap<>();

		// Intialize the content-encoding:
		this.statusCode = getStatusCode();
		this.contentEncoding = getContentEncoding();
		String[] contentTypeAndCharset = getContentTypeAndCharset();
		contentType = contentTypeAndCharset[0];
		charset = contentTypeAndCharset[1] != null ? contentTypeAndCharset[1] : "iso-8859-1";
		contentLength = getContentLength();
		jsessionId = getJsessionId();
	}

	@Override
	public int statusCode() {
		return statusCode;
	}

	@Override
	public String contentType() {
		return contentType;
	}

	@Override
	public String charset() {
		return charset;
	}

	@Override
	public int contentLength() {
		return contentLength;
	}

	@Override
	public String jsessionId() {
		return jsessionId;
	}

	@Override
	public Http2Headers headers() {
		return headers;
	}

	@Override
	public byte[] entity() {

		byte[] cachedData = null;
		if (cacheFile != null) {
			try {
				if (Files.size(cacheFile) > 0) {
					cachedData = Files.readAllBytes(cacheFile);
				}
			} catch (IOException e) {
				System.out.println("Unable to get content from cacheFile: " + e);
				cachedData = "Error in Reading Cache".getBytes();
			}
		}

		return cachedData == null ? entity : combine(cachedData, entity);

	}

	@Override
	public InputStream entityAsInputStream() {

		if (cacheFile != null) {
			try {
				Files.write(cacheFile, entity, StandardOpenOption.APPEND);
				return Files.newInputStream(cacheFile);
			} catch (IOException e) {
				log("Unable to create InputStream: " + e);
				e.printStackTrace(System.out);
				return null;
			}
		} else {
			return new ByteArrayInputStream(entity);
		}

	}

	@Override
	public Http2Headers trailers() {
		return trailers;
	}

	@Override
	public Map<PushRequest, Http2Response> pushedResponses() {
		return pushedResponses;
	}

	/**
	 * Retrieve the response body as string according to the Charset
	 * 
	 * @return
	 */
	@Override
	public String getContent() {

		byte[] data = entity();

		if (data == null) {
			return "";
		}

		String text = bytes2String(data, charset);

		return text;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(headers);
		if (entity != null) {
			sb.append("\r\n\r\n");
			if (contentType != null && (contentType.startsWith("text") || contentType.endsWith("json"))) {
				if (cacheFile != null) {
					sb.append("{cached content}" + showPartOfTextIfTooLong(bytes2String(entity, charset)));
				} else {
					sb.append(showPartOfTextIfTooLong(getContent()));
				}
			} else {
				sb.append("{binary data}");
			}
		}
		if (trailers != null) {
			sb.append("\r\n\r\n");
			sb.append(trailers);
		}
		return sb.toString();
	}

	void setComplete(boolean complete) {
		debugResponse(streamId, this);
		this.complete = complete;
	}

	boolean isComplete() {
		return complete;
	}

	/**
	 * Set the response conent as binary data
	 * 
	 * @param entity
	 *            response content as binary
	 */
	void entity(byte[] entity) {
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

	void cacheFile(Path cacheFile) {
		this.cacheFile = cacheFile;
	}

	void trailers(Http2Headers trailers) {
		this.trailers = trailers;
	}

	private int getStatusCode() {
		return Integer.parseInt(headers.first(PseudoHeader.STATUS.value()).getValue());
	}

	private String getContentEncoding() {
		String encoding = null;
		Header contentEncodingHeader = this.headers.first("content-encoding");
		if (contentEncodingHeader != null) {
			encoding = contentEncodingHeader.getValue();
		}
		return encoding;
	}

	private String[] getContentTypeAndCharset() {
		String[] ret = new String[2];
		Header contentTypeHeader = headers.first("content-type");
		if (contentTypeHeader != null) {
			String contentType = contentTypeHeader.getValue().toLowerCase();
			if (contentType.contains(";")) {
				String[] arr = contentType.split(";");
				contentType = arr[0];
				if (arr[1].contains("charset")) {
					int index = arr[1].indexOf("charset=");
					String charset = arr[1].substring(index + 8);
					charset = charset.replaceAll("(['\"]?)(.*?)(['\"]?)", "$2");
					ret[1] = charset;
				}
			}
			ret[0] = contentType;
		}
		return ret;
	}

	private int getContentLength() {
		Header contentLengthHeader = this.headers.first("content-length");
		if (contentLengthHeader != null) {
			return Integer.parseInt(contentLengthHeader.getValue());
		} else {
			return -1;
		}
	}

	private String getJsessionId() {
		String jsessionId = null;
		Pattern cookiePattern = Pattern.compile(".*?([^=]+)=([^;]+?);.*", Pattern.CASE_INSENSITIVE);
		Header[] setCookieHeaders = headers.all("set-cookie");
		for (Header header : setCookieHeaders) {
			String setCookie = header.getValue().trim();
			Matcher cookieMatcher = cookiePattern.matcher(setCookie);
			if (cookieMatcher.matches()) {
				String key = cookieMatcher.group(1);
				String val = cookieMatcher.group(2);
				if ("jsessionid".equals(key.toLowerCase())) {
					jsessionId = val;
				}
			}
		}
		return jsessionId;
	}

}
