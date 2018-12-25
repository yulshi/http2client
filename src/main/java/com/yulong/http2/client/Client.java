package com.yulong.http2.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yulong.http2.client.Connection.StartBy;
import com.yulong.http2.client.message.Header;
import com.yulong.http2.client.message.Http2Request;
import com.yulong.http2.client.message.Http2Response;
import com.yulong.http2.client.message.PseudoHeader;

/**
 * A client that connects to a dedicated server:port
 */
public class Client implements AutoCloseable {

	private Connection conn;
	private final List<Cookie> cookies;
	private boolean urlRewriting = false;

	public Client(StartBy startBy, String host, int port) throws Http2StartingException {
		this.conn = new ConnectionFactory().create(startBy, host, port);
		this.cookies = new ArrayList<>();
	}

	public Http2Request.Builder newRequestBuilder() {
		return new Http2Request.Builder(conn);
	}

	public Http2Response send(Http2Request request) throws ConnectionException {

		if (urlRewriting) {
			if (!cookies.isEmpty()) {

				StringBuilder sb = new StringBuilder();
				for (Cookie cookie : cookies) {
					sb.append(";").append(cookie.name.toLowerCase()).append("=").append(cookie.value);
				}

				Header pathHeader = request.headers().first(PseudoHeader.PATH.value());
				String path = pathHeader.getValue();

				int index = path.indexOf("?");
				if (index > -1) {
					String rootPath = path.substring(0, index);
					String queryString = path.substring(index);
					path = rootPath + sb.toString() + queryString;
				} else {
					path = path + sb.toString();
				}

				request.headers().set(new Header(PseudoHeader.PATH.value(), path));
			}

		} else {

			// Set Cookie header if existing:
			if (!cookies.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < cookies.size(); i++) {
					if (i > 0) {
						sb.append(" ;");
					}
					sb.append(cookies.get(i).toString());
				}
				request.headers().add("Cookie", sb.toString());
			}

		}

		// Send the request:
		Http2Response response = request.send();

		// Update the client cookie if necessary:
		Pattern cookiePattern = Pattern.compile(".*?([^=]+)=([^;]+?);.*", Pattern.CASE_INSENSITIVE);
		Header[] setCookieHeaders = response.headers().all("set-cookie");
		for (Header header : setCookieHeaders) {
			String setCookie = header.getValue().trim();
			Matcher cookieMatcher = cookiePattern.matcher(setCookie);
			if (cookieMatcher.matches()) {
				Cookie cookie = new Cookie(cookieMatcher.group(1), cookieMatcher.group(2));
				if (cookies.contains(cookie)) {
					cookies.remove(cookie);
				}
				cookies.add(cookie);
			}
		}

		// redirect if the status code is 302 or 303:
		int statusCode = response.statusCode();
		if (statusCode == 302 || statusCode == 303) {

			// Get the redirected path:
			String redirectPath = null;
			String location = response.headers().first("location").getValue();

			Pattern pathPattern = Pattern.compile("http[s]?://[^:]+?:[^/]+(.*)", Pattern.CASE_INSENSITIVE);
			Matcher pathMatcher = pathPattern.matcher(location);
			if (pathMatcher.matches()) {
				redirectPath = pathMatcher.group(1);
			}

			assert redirectPath != null;

			Http2Request redirect = newRequestBuilder().get(redirectPath).build();
			response = send(redirect);

		}

		return response;

	}

	public void enableUrlRewriting() {
		this.urlRewriting = true;
	}

	@Override
	public void close() throws IOException {
		if (conn != null) {
			conn.close();
		}
	}

	public static class Cookie {

		private String name;
		private String value;

		public Cookie(String name, String value) {
			super();
			this.name = name;
			this.value = value;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Cookie) {
				Cookie o = (Cookie) obj;
				return this.name.equals(o.name);
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return name + "=" + value;
		}

	}

}
