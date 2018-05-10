package com.yulong.http2.client.frame;

public class FrameHistory {

	private Frame frame;
	private long timestamp;

	public FrameHistory(Frame frame) {
		this(frame, System.currentTimeMillis());
	}

	private FrameHistory(Frame frame, long timestamp) {
		this.frame = frame;
		this.timestamp = timestamp;
	}

	public Frame getFrame() {
		return frame;
	}

	public long getTimestamp() {
		return timestamp;
	}

}
