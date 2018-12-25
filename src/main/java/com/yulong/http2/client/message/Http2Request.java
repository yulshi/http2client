package com.yulong.http2.client.message;

import static java.util.Base64.getEncoder;
import static com.yulong.http2.client.utils.Utils.fragment;
import static com.yulong.http2.client.utils.Utils.string2Bytes;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yulong.http2.client.Connection;
import com.yulong.http2.client.ConnectionException;
import com.yulong.http2.client.Stream;
import com.yulong.http2.client.common.ErrorCodeRegistry;
import com.yulong.http2.client.frame.ContinuationFrame;
import com.yulong.http2.client.frame.DataFrame;
import com.yulong.http2.client.frame.HeadersFrame;
import com.yulong.http2.client.netty.Http2ResponseImpl;
import com.yulong.http2.client.utils.Utils;

/**
 * An HTTP/2 request
 */
public final class Http2Request {

	private final Connection connection;
	private Http2Headers headers;
	private byte[] entity;
	private Http2Headers trailers;

	private Http2Request(Connection connection) {
		this.connection = connection;
	}

	public Http2Headers headers() {
		return headers;
	}

	public byte[] entity() {
		return entity;
	}

	public Http2Headers trailers() {
		return trailers;
	}

	/**
	 * Send a request without taking into account cookies, redirection etc.
	 * 
	 * @return
	 * @throws ConnectionException
	 */
	public Http2Response send() throws ConnectionException {
		return send(-1, 0);
	}

	/**
	 * Send a request without taking into account cookies, redirection etc.
	 * 
	 * @return
	 * @throws ConnectionException
	 */
	public Http2Response send(int maxDataFrameSize, int waitSecondsSendingDataFrame) throws ConnectionException {
		return send(maxDataFrameSize, waitSecondsSendingDataFrame, true);
	}

	/**
	 * Send a request without taking into account cookies, rediection etc. Some fine-grained control can
	 * be given to the process sending a request.
	 * 
	 * @param maxDataFrameSize maximum DATA frame size
	 * @param waitSecondsSendingDataFrame the interval in seconds in between sending two DATA frames
	 * @param sendWindowUpdate if send WINDOW_UPDATE frame to increment server's window size
	 * @return Http2Response instance
	 * @throws ConnectionException
	 */
	public Http2Response send(int maxDataFrameSize, int waitSecondsSendingDataFrame, boolean sendWindowUpdate)
			throws ConnectionException {

		int settingsMaxFrameSize = connection.currentSettings().getMaxFrameSize();

		try (Stream stream = connection.newStream()) {

			if (sendWindowUpdate) {
				stream.addDefaultDataFrameConsumer();
			}

			// Send request headers:
			sendHeaderOrTrailer(stream, headers, settingsMaxFrameSize, entity == null);

			// Send request body:
			if (entity != null) {

				if (maxDataFrameSize <= 0 || maxDataFrameSize > settingsMaxFrameSize) {
					maxDataFrameSize = settingsMaxFrameSize;
				}

				int entityLength = entity.length;
				if (entityLength <= maxDataFrameSize) {
					// If the data size is smaller than maxDataFrameSize, send it in one DATA frame:
					stream.data(new DataFrame(stream.getId(), trailers == null, entity));
				} else {
					// If the data size is larger, send more DATA frames:
					List<byte[]> dataFragments = fragment(entity, maxDataFrameSize);

					int fragmentSize = dataFragments.size();

					boolean endStreamFlag = false;
					for (int i = 0; i < dataFragments.size(); i++) {
						if (i == fragmentSize - 1) {
							endStreamFlag = trailers == null;
						} else {
							endStreamFlag = false;
						}
						if (i == 0) {
							// skip waiting:
						} else {
							if (waitSecondsSendingDataFrame > 0) {
								try {
									Thread.sleep(waitSecondsSendingDataFrame * 1000);
								} catch (InterruptedException e) {
									System.out.println("Failed to make the current thread to sleep: " + e);
								}
							}
						}
						stream.data(new DataFrame(stream.getId(), endStreamFlag, dataFragments.get(i)));
					}

				}

			}

			// Send trailers if any:
			if (trailers != null) {
				sendHeaderOrTrailer(stream, trailers, settingsMaxFrameSize, true);
			}

			// Wait to get the response:
			Http2ResponseImpl response = (Http2ResponseImpl) stream.getResponse();

			// Receive the pushed responses if any:
			for (Map.Entry<PushRequest, Stream> entry : stream.promisedStreams().entrySet()) {
				PushRequest pushReq = entry.getKey();
				Stream promisedStream = entry.getValue();
				response.pushedResponses().put(pushReq, promisedStream.getResponse());
			}

			return response;

		} catch (IOException e) {
			e.printStackTrace(System.out);
			throw new ConnectionException(ErrorCodeRegistry.UNKNOWN, e.toString());
		}

	}

	/**
	 * Send HEADERS frame for either HTTP headers or trailers
	 * 
	 * @param stream
	 * @param http2Headers
	 * @param settingsMaxFrameSize
	 * @param endStream
	 * @throws ConnectionException
	 */
	private static void sendHeaderOrTrailer(Stream stream, Http2Headers http2Headers, int settingsMaxFrameSize,
			boolean endStream) throws ConnectionException {

		byte[] headerBlock = http2Headers.toHeaderBlock();

		List<byte[]> fragments = fragment(headerBlock, settingsMaxFrameSize);

		if (fragments.size() > 1) {
			HeadersFrame headersFrame = new HeadersFrame(stream.getId(), endStream, false, fragments.get(0));
			stream.headers(headersFrame);
			for (int i = 1; i < fragments.size() - 1; i++) {
				ContinuationFrame cf = new ContinuationFrame(stream.getId(), false, fragments.get(i));
				stream.continuation(cf);
			}
			ContinuationFrame cf = new ContinuationFrame(stream.getId(), true, fragments.get(fragments.size() - 1));
			stream.continuation(cf);
		} else {
			HeadersFrame headersFrame = new HeadersFrame(stream.getId(), endStream, true, headerBlock);
			stream.headers(headersFrame);
		}

	}

	public static class Builder {

		private Http2Request request;
		private Connection connection;

		public Builder(Connection connection) {
			this.connection = connection;
			request = new Http2Request(connection);
		}

		public Builder method(String method, String path) {
			request.headers = new Http2Headers(connection, method, path);
			return this;
		}

		public Builder get(String path) {
			return method("GET", path);
		}

		public Builder post(String path) {
			return method("POST", path);
		}

		public Builder basicAuth(String username, String password) {
			String basicAuthString = getEncoder().encodeToString(string2Bytes(username + ":" + password));
			request.headers.add(new Header("Authorization", "Basic " + basicAuthString));
			return this;
		}

		public Builder headers(Header... headers) {
			for (Header header : headers) {
				request.headers.add(header);
			}
			return this;
		}

		public Builder entity(Params params) {
			entity(Utils.string2Bytes(params.toString()), "application/x-www-form-urlencoded;charset=utf-8");
			return this;
		}

		public Builder entity(byte[] data, String contentType) {
			request.entity = data;
			request.headers.add(new Header("Content-Type", contentType));
			request.headers.add(new Header("Content-Length", Integer.toString(request.entity.length)));
			return this;
		}

		public Builder trailers(Header... headers) {
			request.trailers = new Http2Headers(connection);
			for (Header header : headers) {
				request.trailers.add(header);
			}
			return this;
		}

		public Http2Request build() {
			return request;
		}

	}

	/**
	 * Request parameters that construct the request body
	 */
	public static class Params {

		private final HashMap<String, Object> parameters = new HashMap<>();

		public Object getParameter(String name) {
			return this.parameters.get(name);
		}

		public Params setParameter(String name, Object value) {
			this.parameters.put(name, value);
			return this;
		}

		public boolean removeParameter(String name) {
			if (this.parameters.containsKey(name)) {
				this.parameters.remove(name);
				return true;
			}
			return false;
		}

		public void setParameters(String[] names, Object value) {
			for (int i = 0; i < names.length; ++i)
				setParameter(names[i], value);
		}

		public boolean isParameterSet(String name) {
			return (getParameter(name) != null);
		}

		public boolean isParameterSetLocally(String name) {
			return (this.parameters.get(name) != null);
		}

		public void clear() {
			this.parameters.clear();
		}

		public long getLongParameter(String name, long defaultValue) {
			Object param = getParameter(name);
			if (param == null) {
				return defaultValue;
			}
			return ((Long) param).longValue();
		}

		public Params setLongParameter(String name, long value) {
			setParameter(name, new Long(value));
			return this;
		}

		public int getIntParameter(String name, int defaultValue) {
			Object param = getParameter(name);
			if (param == null) {
				return defaultValue;
			}
			return ((Integer) param).intValue();
		}

		public Params setIntParameter(String name, int value) {
			setParameter(name, new Integer(value));
			return this;
		}

		public double getDoubleParameter(String name, double defaultValue) {
			Object param = getParameter(name);
			if (param == null) {
				return defaultValue;
			}
			return ((Double) param).doubleValue();
		}

		public Params setDoubleParameter(String name, double value) {
			setParameter(name, new Double(value));
			return this;
		}

		public boolean getBooleanParameter(String name, boolean defaultValue) {
			Object param = getParameter(name);
			if (param == null) {
				return defaultValue;
			}
			return ((Boolean) param).booleanValue();
		}

		public Params setBooleanParameter(String name, boolean value) {
			setParameter(name, (value) ? Boolean.TRUE : Boolean.FALSE);
			return this;
		}

		public boolean isParameterTrue(String name) {
			return getBooleanParameter(name, false);
		}

		public boolean isParameterFalse(String name) {
			return (!(getBooleanParameter(name, false)));
		}

		public Params setTextParameter(String name, String value) {
			setParameter(name, value);
			return this;
		}

		public String getTextParameter(String name) {
			Object param = getParameter(name);
			if (param == null) {
				return null;
			}
			return param.toString();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, Object> entry : parameters.entrySet()) {
				String name = entry.getKey();
				String value = entry.getValue().toString();
				try {
					name = URLEncoder.encode(name, "utf-8");
					value = URLEncoder.encode(value, "utf-8");
				} catch (UnsupportedEncodingException e) {
					System.out.println("WARN: failed to encode param: " + e);
					e.printStackTrace(System.out);
				}
				sb.append(name + "=" + value).append("&");
			}
			if (sb.length() != 0) {
				return sb.substring(0, sb.length() - 1);
			} else {
				return sb.toString();
			}
		}

	}

}
