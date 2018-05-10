package com.yulong.http2.client.frame;

import static com.yulong.http2.client.utils.Utils.createBinaryData;
import static com.yulong.http2.client.utils.Utils.toHexString;

public class PingFrame extends Frame {

	public static final int PAYLOAD_LENGTH = 8;

	private final boolean ack;
	private final byte[] opaqueData;

	public PingFrame() {
		this(false);
	}

	public PingFrame(byte[] opaqueData) {
		this(false, opaqueData);
	}

	public PingFrame(boolean ack) {
		this(ack, createBinaryData(PAYLOAD_LENGTH, new byte[] { (byte) 0 }));
	}

	public PingFrame(boolean ack, byte[] opaqueData) {

		this.ack = ack;
		this.opaqueData = opaqueData;

		byte flags = (byte) (this.ack ? 0x1 : 0x0);

		init(8, FrameType.PING, flags, 0, opaqueData);
	}

	public PingFrame(Frame rawFrame) {
		copyFrom(rawFrame);
		this.ack = (rawFrame.getFlags() & 0x1) == 0x1;
		this.opaqueData = rawFrame.getPayload();
	}

	public boolean isAck() {
		return ack;
	}

	public byte[] getOpaqueData() {
		return opaqueData;
	}

	@Override
	public String describeFlags() {
		return "ACK=" + ack;
	}

	@Override
	public String describePayload() {
		return "Opaque-Data=" + toHexString(opaqueData);
	}

}
