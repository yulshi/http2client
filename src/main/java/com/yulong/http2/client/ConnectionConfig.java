package com.yulong.http2.client;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import com.yulong.http2.client.frame.FrameHistory;
import com.yulong.http2.client.frame.SettingsFrame;

public class ConnectionConfig {

	private byte[] connectionPreface;
	private boolean sendAcknowledgePrefaceImmediately;
	private List<Consumer<FrameHistory>> frameConsumers;
	private boolean drainStreamOnClose;

	public ConnectionConfig() {
		connectionPreface = SettingsFrame.EMPTY.toConnectionPreface();
		sendAcknowledgePrefaceImmediately = true;
		frameConsumers = new LinkedList<>();
		drainStreamOnClose = false;
	}

	public byte[] getConnectionPreface() {
		return connectionPreface;
	}

	public ConnectionConfig setConnectionPreface(byte[] connectionPreface) {
		this.connectionPreface = connectionPreface;
		return this;
	}

	public boolean isSendAcknowledgePrefaceImmediately() {
		return sendAcknowledgePrefaceImmediately;
	}

	public ConnectionConfig setSendAcknowledgePrefaceImmediately(boolean sendAcknowledgePrefaceImmediately) {
		this.sendAcknowledgePrefaceImmediately = sendAcknowledgePrefaceImmediately;
		return this;
	}

	public List<Consumer<FrameHistory>> getFrameConsumers() {
		return frameConsumers;
	}

	public ConnectionConfig addFrameConsumer(Consumer<FrameHistory> frameConsumer) {
		this.frameConsumers.add(frameConsumer);
		return this;
	}

	public boolean isDrainStreamOnClose() {
		return drainStreamOnClose;
	}

	public ConnectionConfig setDrainStreamOnClose(boolean drainStreamOnClose) {
		this.drainStreamOnClose = drainStreamOnClose;
		return this;
	}

}
