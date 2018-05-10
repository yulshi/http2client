package com.yulong.http2.client.common;

import java.util.HashMap;
import java.util.Map;

public enum ErrorCodeRegistry {

	NO_ERROR(0x0), 
	PROTOCOL_ERROR(0x1), 
	INTERNAL_ERROR(0x2), 
	FLOW_CONTROL_ERROR(0x3), 
	SETTINGS_TIMEOUT(0x4), 
	STREAM_CLOSED(0x5), 
	FRAME_SIZE_ERROR(0x6), 
	REFUSED_STREAM(0x7), 
	CANCEL(0x8), 
	COMPRESSION_ERROR(0x9), 
	CONNECT_ERROR(0xA), 
	ENHANCE_YOUR_CALM(0xB), 
	INADEQUATE_SECURITY(0xC), 
	HTTP_1_1_REQUIRED(0xD),
	PING_TIMEOUT(0x10),
	UNKNOWN(0xff);

	private final int code;

	ErrorCodeRegistry(int code) {
		this.code = code;
		R.registry.put(code, this);
	}

	public int getCode() {
		return code;
	}

	public static ErrorCodeRegistry from(int code) {
		return R.registry.get(code);
	}

	private static class R {
		private static final Map<Integer, ErrorCodeRegistry> registry = new HashMap<Integer, ErrorCodeRegistry>();
	}

}
