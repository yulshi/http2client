package com.yulong.http2.client.frame;

import java.text.SimpleDateFormat;
import java.util.Date;

public class FrameHistory {

	private static final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss SSS");

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

	@Override
	public String toString() {
		Date date = new Date(timestamp);
		return "[Frame Hitory] [" + sdf.format(date) + "] " + frame.toString();
	}

}
