package com.yulong.http2.client.message;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP/2 headers
 * 
 * @author yushi
 * @since 12.2.2.1.0 Jan 11 2016
 *
 */
public class Http2Headers {

	private List<Header> headers;

	public Http2Headers() {
		this(null, null, null, null);
	}

	public Http2Headers(String method, String path, String authority, String scheme) {
		this.headers = new ArrayList<Header>(16);
		if (method != null) {
			add(PseudoHeader.METHOD.value(), method);
		}
		if (path != null) {
			add(PseudoHeader.PATH.value(), path);
		}
		if (authority != null) {
			add(PseudoHeader.AUTHORITY.value(), authority);
		}
		if (scheme != null) {
			add(PseudoHeader.SCHEME.value(), scheme);
		}
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
			desc.append(header.getName() + ": " + header.getValue()).append("\r\n");
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
