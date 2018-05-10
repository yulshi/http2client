package com.yulong.http2.client.common;

import java.util.HashMap;
import java.util.Map;

public enum SettingsRegistry {

	HEADER_TABLE_SIZE(1), 
	ENABLE_PUSH(2), 
	MAX_CONCURRENT_STREAMS(3), 
	INITIAL_WINDOW_SIZE(4), 
	MAX_FRAME_SIZE(5), 
	MAX_HEADER_LIST_SIZE(6),

	USER_DEFINED_4_TESTING(10);

	private final int code;

	SettingsRegistry(int code) {
		this.code = code;
		R.registry.put(code, this);
	}

	public int getCode() {
		return code;
	}

	public static SettingsRegistry from(int code) {
		return R.registry.get(code);
	}

	private static class R {
		private static final Map<Integer, SettingsRegistry> registry = new HashMap<>();
	}

}
