package com.yulong.http2.client.frame;

import static com.yulong.http2.client.frame.FrameType.PRIORITY;
import static com.yulong.http2.client.utils.Utils.toInt;

import java.nio.ByteBuffer;

public final class PriorityFrame extends Frame {

	public static final int PAYLOAD_LENGTH = 5;

	private final boolean exclusive;
	private final int parentStreamId;
	private final int weight;

	public PriorityFrame(int streamId, boolean exclusive, int parentStreamId, int weight) {

		this.exclusive = exclusive;
		this.parentStreamId = parentStreamId;
		this.weight = weight;

		int payloadLength = PAYLOAD_LENGTH;
		ByteBuffer byteBuff = ByteBuffer.allocate(payloadLength);
		byteBuff.putInt(this.parentStreamId);
		byteBuff.put((byte) (weight - 1));

		byte firstByte = byteBuff.get(0);
		if (this.exclusive) {
			firstByte = (byte) (firstByte | 0x80);
		} else {
			firstByte = (byte) (firstByte & 0x7f);
		}
		byteBuff.put(0, firstByte);

		init(payloadLength, PRIORITY, (byte) 0, streamId, byteBuff.array());

	}

	public PriorityFrame(Frame rawFrame) {

		copyFrom(rawFrame);

		byte firstByte = getPayload()[0];
		this.exclusive = ((firstByte & 0x80) == 0x80);

		ByteBuffer payloadBuf = ByteBuffer.wrap(getPayload());
		payloadBuf.put((byte) (firstByte & 0x7f));
		payloadBuf.clear();
		this.parentStreamId = payloadBuf.getInt();
		this.weight = toInt(new byte[] { payloadBuf.get() }) + 1;

	}

	public PriorityFrame(int streamId, byte[] payload) {
		this(new Frame(5, PRIORITY, (byte) 0, streamId, payload));
	}

	@Override
	public String describeFlags() {
		return "";
	}

	@Override
	public String describePayload() {
		StringBuilder describe = new StringBuilder();
		describe.append("E=").append(isExclusive()).append(keyValSep);
		describe.append("Stream-Dependency=").append(getParentStreamId()).append(keyValSep);
		describe.append("Weight=").append(getWeight());
		return describe.toString();
	}

	public boolean isExclusive() {
		return exclusive;
	}

	public int getParentStreamId() {
		return parentStreamId;
	}

	public int getWeight() {
		return weight;
	}

}
