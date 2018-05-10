package com.yulong.http2.client.frame;

import static com.yulong.http2.client.utils.Utils.combine;
import static com.yulong.http2.client.utils.Utils.fromInt;
import static com.yulong.http2.client.utils.Utils.toHexString;
import static com.yulong.http2.client.utils.Utils.toInt;

import java.nio.ByteBuffer;

import com.yulong.http2.client.ConnectionException;
import com.yulong.http2.client.common.ErrorCodeRegistry;

public class Frame {

	public static final String keyValSep = ";";

	private int _payloadLength;
	private FrameType _type;
	private byte _flags;
	private int _streamId;
	private byte[] _payload;
	private byte[] _rawData;

	private byte[] header = null;

	public Frame() {
		init(0, FrameType.UNKNOWN, (byte) 0, 0, new byte[0]);
	}

	public Frame(int payloadLength, FrameType type, byte flags, int streamId, byte[] payload) {
		init(payloadLength, type, flags, streamId, payload);
	}

	public Frame(byte[] rawData) throws ConnectionException {
		this._rawData = rawData;

		ByteBuffer rawDataBuff = ByteBuffer.wrap(_rawData);

		byte[] lengthAsBytes = new byte[3];
		rawDataBuff.get(lengthAsBytes);
		this._payloadLength = toInt(lengthAsBytes);

		byte type = rawDataBuff.get();
		this._type = FrameType.from(type);
		if (this._type == FrameType.UNKNOWN) {
			throw new ConnectionException(ErrorCodeRegistry.PROTOCOL_ERROR, "Unknown frame type code: " + type);
		}
		this._flags = rawDataBuff.get();
		this._streamId = rawDataBuff.getInt();

		this._payload = new byte[_payloadLength];
		rawDataBuff.get(_payload);
	}

	protected void copyFrom(Frame another) {
		init(another.getPayloadLength(), another.getType(), another.getFlags(), another.getStreamId(),
				another.getPayload());
	}

	protected void init(int payloadLength, FrameType type, byte flags, int streamId, byte[] payload) {
		this._payloadLength = payloadLength;
		this._type = type;
		this._flags = flags;
		this._streamId = streamId;
		this._payload = payload;

		_rawData = combine(getHeader(), this._payload);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getType().name()).append("[");
		sb.append("Length: ").append(getPayloadLength()).append(", ");
		sb.append("Flags: (").append(describeFlags()).append("), ");
		sb.append("Stream-ID: ").append(getStreamId()).append(", ");
		sb.append("Payload: (").append(describePayload()).append(")");
		sb.append("]");
		return sb.toString();
	}

	public String describeFlags() {
		return "";
	}

	public String describePayload() {
		return toHexString(getPayload());
	}

	public int getPayloadLength() {
		return _payloadLength;
	}

	public FrameType getType() {
		return _type;
	}

	public byte getFlags() {
		return _flags;
	}

	public int getStreamId() {
		return _streamId;
	}

	public synchronized byte[] getHeader() {
		if (header == null) {
			ByteBuffer bb = ByteBuffer.allocate(9);
			bb.put(fromInt(_payloadLength, 3));
			bb.put((byte) (_type.getCode() & 0xff));
			bb.put((byte) (_flags & 0xff));
			bb.putInt(_streamId);
			return bb.array();
		} else {
			return header;
		}
	}

	public byte[] getPayload() {
		return _payload;
	}

	public byte[] asBytes() {
		return _rawData;
	}

	@SuppressWarnings("unchecked")
	public final <T extends Frame> T as(Class<T> clazz) {
		return (T) this;
	}

}
