package com.yulong.http2.client.message;

import static com.yulong.http2.client.utils.Utils.showPartOfTextIfTooLong;

import java.util.ArrayList;
import java.util.List;

import com.yulong.http2.client.Connection;
import com.yulong.http2.client.ConnectionException;

/**
 * HTTP/2 headers
 */
public class Http2Headers {

	private List<Header> headers;
	private Connection connection;

	public Http2Headers(Connection connection) {
		this(connection, null, null);
	}

	public Http2Headers(Connection connection, String method, String path) {
		this.headers = new ArrayList<Header>(16);
		this.connection = connection;
		if (method != null) {
			add(PseudoHeader.METHOD.value(), method);
			if (path != null) {
				add(PseudoHeader.PATH.value(), System.getProperty("http2.path.prefix", "") + path);
			}
			add(PseudoHeader.AUTHORITY.value(), this.connection.getHost() + ":" + this.connection.getPort());
			add(PseudoHeader.SCHEME.value(), this.connection.getScheme());
		}
	}

	public byte[] toHeaderBlock() {
		//		if (!contains("User-Agent")) {
		//			add(new Header("User-Agent", "wlstest.http2.client/1.0"));
		//		}
		return connection.encode(this);
	}

	public static Http2Headers fromHeaderBlock(Connection connection, byte[] headerBlock) throws ConnectionException {
		return connection.decode(headerBlock);
	}

	public void clear() {
		this.headers.clear();
	}

	public Http2Headers add(Header header) {
		this.headers.add(header);
		return this;
	}

	public Http2Headers add(String name, String value) {
		return add(new Header(name, value));
	}

	public Http2Headers set(Header header) {
		for (int i = 0; i < this.headers.size(); ++i) {
			Header current = (Header) this.headers.get(i);
			if (current.getName().equalsIgnoreCase(header.getName())) {
				this.headers.set(i, header);
				return this;
			}
		}
		return add(header);
	}

	public Http2Headers set(Header[] headers) {
		clear();
		for (int i = 0; i < headers.length; ++i) {
			this.headers.add(headers[i]);
		}
		return this;
	}

	public Http2Headers remove(Header header) {
		this.headers.remove(header);
		return this;
	}

	public Header[] all(String name) {

		List<Header> headersFound = new ArrayList<>();

		for (Header header : headers) {
			if (header.getName().equalsIgnoreCase(name)) {
				headersFound.add(header);
			}
		}

		return (Header[]) headersFound.toArray(new Header[headersFound.size()]);
	}

	public Header first(String name) {

		for (Header header : headers) {
			if (header.getName().equalsIgnoreCase(name)) {
				return header;
			}
		}
		return null;

	}

	public Header last(String name) {

		Header[] headers = all(name);
		if (headers.length != 0) {
			return headers[headers.length - 1];
		} else {
			return null;
		}

	}

	public Header condensed(String name) {

		Header[] headers = all(name);

		if (headers.length == 0)
			return null;

		if (headers.length == 1) {
			return headers[0];
		}

		StringBuilder sb = new StringBuilder();
		sb.append(headers[0].getValue());
		for (int i = 1; i < headers.length; ++i) {
			sb.append(", ").append(headers[i].getValue());
		}

		return new Header(name, sb.toString());

	}

	public Header[] all() {
		return (Header[]) this.headers.toArray(new Header[this.headers.size()]);
	}

	public boolean contains(String name) {

		for (Header header : headers) {
			if (header.getName().equalsIgnoreCase(name)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public String toString() {

		StringBuilder desc = new StringBuilder();

		for (Header header : headers) {
			desc.append(header.getName() + ": " + showPartOfTextIfTooLong(header.getValue())).append("\r\n");
		}

		return desc.substring(0, desc.length() - 2);

	}

	public int size() {
		return headers.size();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((headers == null) ? 0 : headers.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Http2Headers other = (Http2Headers) obj;
		if (headers == null) {
			if (other.headers != null)
				return false;
		} else if (!headers.equals(other.headers))
			return false;
		return true;
	}

}
