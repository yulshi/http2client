package com.yulong.http2.client.frame;

import java.nio.ByteBuffer;

public class WindowUpdateFrame extends Frame {

	public static final int PAYLOAD_LENGTH = 4;

	private final boolean reservedBit;
	private final int windowSizeIncrement;

	public WindowUpdateFrame(int streamId, int windowSizeIncrement) {
		this(streamId, windowSizeIncrement, false);
	}

	public WindowUpdateFrame(int streamId, int windowSizeIncrement, boolean reservedBit) {

		this.windowSizeIncrement = windowSizeIncrement;
		this.reservedBit = reservedBit;

		ByteBuffer payloadBuff = ByteBuffer.allocate(4);
		payloadBuff.putInt(this.windowSizeIncrement);

		byte firstByte = payloadBuff.get(0);
		if (this.reservedBit) {
			firstByte = (byte) (firstByte | 0x80);
		} else {
			firstByte = (byte) (firstByte & 0x7f);
		}
		payloadBuff.put(0, firstByte);

		init(PAYLOAD_LENGTH, FrameType.WINDOW_UPDATE, (byte) 0, streamId, payloadBuff.array());

	}

	public WindowUpdateFrame(Frame rawFrame) {

		copyFrom(rawFrame);

		ByteBuffer payloadBuff = ByteBuffer.wrap(rawFrame.getPayload());

		byte firstByte = payloadBuff.get(0);
		this.reservedBit = (firstByte & 0x80) == 0x80;
		firstByte &= 0x7f;
		payloadBuff.put(0, firstByte);

		this.windowSizeIncrement = payloadBuff.getInt();

	}

	@Override
	public String describePayload() {
		return "R=" + reservedBit + keyValSep + "Window-Size-Increment=" + windowSizeIncrement;
	}

	public boolean isReservedBit() {
		return reservedBit;
	}

	public int getWindowSizeIncrement() {
		return windowSizeIncrement;
	}

}
