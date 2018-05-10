package com.yulong.http2.client.message;

import java.util.HashMap;

/**
 * An HTTP/2 request
 * 
 * @author yushi
 *
 */
public class Http2Request {

	private final String method;
	private final Http2Headers headers;
	private Params params;

	public Http2Request(String method, String scheme, String authority, String path) {

		this.method = method;
		headers = new Http2Headers();
		if (authority != null) {
			headers.add(PseudoHeader.AUTHORITY.value(), authority);
		}
		headers.add(PseudoHeader.METHOD.value(), method);
		headers.add(PseudoHeader.PATH.value(), path);
		headers.add(PseudoHeader.SCHEME.value(), scheme);

		this.params = null;

	}

	public String getMethod() {
		return method;
	}

	public Http2Headers headers() {
		return headers;
	}

	public Params getParams() {
		return params;
	}

	public void setParams(Params params) {
		this.params = params;
	}

	/**
	 * Request parameters that construct the request body
	 * 
	 * @author yushi
	 *
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

	}

}
