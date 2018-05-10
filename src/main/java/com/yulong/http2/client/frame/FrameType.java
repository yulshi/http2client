package com.yulong.http2.client.frame;

import java.util.HashMap;
import java.util.Map;

public enum FrameType {

	DATA(0), 
	HEADERS(1), 
	PRIORITY(2), 
	RST_STREAM(3), 
	SETTINGS(4), 
	PUSH_PROMISE(5), 
	PING(6), 
	GO_AWAY(7), 
	WINDOW_UPDATE(8), 
	CONTINUATION(9), 
	UNKNOWN(100);

	private final int code;

	FrameType(int code) {
		this.code = code;
		Type.map.put(this.code, this);
	}

	public int getCode() {
		return this.code;
	}

	public static FrameType from(int code) {
		FrameType type = Type.map.get(code);
		if (type == null) {
			return FrameType.UNKNOWN;
		} else {
			return type;
		}
	}

	private static class Type {
		private static final Map<Integer, FrameType> map = new HashMap<>();
	}

}
