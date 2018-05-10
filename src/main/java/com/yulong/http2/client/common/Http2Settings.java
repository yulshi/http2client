package com.yulong.http2.client.common;

import java.util.Map;

import com.yulong.http2.client.frame.SettingsFrame;

public class Http2Settings {

	private int headerTableSize = 4096;
	private boolean enablePush = true;
	private int maxConcurrentStreams = Integer.MAX_VALUE;
	private int initialWindowSize = 65535; // 2^16 - 1
	private int maxFrameSize = 16384; // 2^14
	private int maxHeaderListSize = Integer.MAX_VALUE;

	public void setFrom(SettingsFrame settingsFrame) {

		for (Map.Entry<SettingsRegistry, Integer> entry : settingsFrame.getSettings().entrySet()) {

			SettingsRegistry name = entry.getKey();
			int value = entry.getValue();

			switch (name) {
			case HEADER_TABLE_SIZE:
				this.headerTableSize = value;
				break;
			case ENABLE_PUSH:
				this.enablePush = value != 0;
				break;
			case MAX_CONCURRENT_STREAMS:
				this.maxConcurrentStreams = value;
				break;
			case INITIAL_WINDOW_SIZE:
				this.initialWindowSize = value;
				break;
			case MAX_FRAME_SIZE:
				this.maxFrameSize = value;
				break;
			case MAX_HEADER_LIST_SIZE:
				this.maxHeaderListSize = value;
				break;
			default:
				break;
			}

		}

	}

	public int getHeaderTableSize() {
		return headerTableSize;
	}

	public void setHeaderTableSize(int headerTableSize) {
		this.headerTableSize = headerTableSize;
	}

	public boolean isEnablePush() {
		return enablePush;
	}

	public void setEnablePush(boolean enablePush) {
		this.enablePush = enablePush;
	}

	public int getMaxConcurrentStreams() {
		return maxConcurrentStreams;
	}

	public void setMaxConcurrentStreams(int maxConcurrentStreams) {
		this.maxConcurrentStreams = maxConcurrentStreams;
	}

	public int getInitialWindowSize() {
		return initialWindowSize;
	}

	public void setInitialWindowSize(int initialWindowSize) {
		this.initialWindowSize = initialWindowSize;
	}

	public int getMaxFrameSize() {
		return maxFrameSize;
	}

	public void setMaxFrameSize(int maxFrameSize) {
		this.maxFrameSize = maxFrameSize;
	}

	public int getMaxHeaderListSize() {
		return maxHeaderListSize;
	}

	public void setMaxHeaderListSize(int maxHeaderListSize) {
		this.maxHeaderListSize = maxHeaderListSize;
	}

	@Override
	public String toString() {
		StringBuilder describe = new StringBuilder();
		describe.append("HEADER_TABLE_SIZE=").append(headerTableSize).append(", ");
		describe.append("ENABLE_PUSH=").append(enablePush).append(",");
		describe.append("MAX_CONCURRENT_STREAMS=").append(maxConcurrentStreams).append(", ");
		describe.append("INITIAL_WINDOW_SIZE=").append(initialWindowSize).append(", ");
		describe.append("MAX_FRAME_SIZE=").append(maxFrameSize).append(", ");
		describe.append("MAX_HEADER_LIST_SIZE=").append(maxHeaderListSize);
		return describe.toString();
	}

}
