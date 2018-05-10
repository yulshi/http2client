package com.yulong.http2.client.frame;

import static com.yulong.http2.client.frame.FrameType.GO_AWAY;
import static com.yulong.http2.client.utils.Utils.bytes2String;
import static com.yulong.http2.client.utils.Utils.string2Bytes;

import java.nio.ByteBuffer;

import com.yulong.http2.client.common.ErrorCodeRegistry;
import com.yulong.http2.client.frame.Frame;

public class GoAwayFrame extends Frame {

	private final int lastStreamId;
	private final ErrorCodeRegistry errorCode;
	private final String debugData;

	public GoAwayFrame(int lastStreamId, ErrorCodeRegistry errorCode, final String debugData) {

		this.lastStreamId = lastStreamId;
		this.errorCode = errorCode;
		this.debugData = debugData;

		byte[] payload = null;
		if (this.debugData != null) {
			byte[] debugDataAsBytes = string2Bytes(this.debugData);
			ByteBuffer payloadBuf = ByteBuffer.allocate(8 + debugDataAsBytes.length);
			payloadBuf.putInt(lastStreamId).putInt(errorCode.getCode()).put(debugDataAsBytes);
			payload = payloadBuf.array();
		} else {
			ByteBuffer payloadBuf = ByteBuffer.allocate(8);
			payloadBuf.putInt(lastStreamId).putInt(errorCode.getCode());
			payload = payloadBuf.array();
		}

		init(payload.length, GO_AWAY, (byte) 0, 0, payload);

	}

	public GoAwayFrame(final Frame rawFrame) {

		copyFrom(rawFrame);

		ByteBuffer payloadBuf = ByteBuffer.wrap(getPayload());
		this.lastStreamId = payloadBuf.getInt();
		this.errorCode = ErrorCodeRegistry.from(payloadBuf.getInt());

		if (payloadBuf.remaining() != 0) {
			byte[] debugDataAsBytes = new byte[payloadBuf.remaining()];
			payloadBuf.get(debugDataAsBytes);
			this.debugData = bytes2String(debugDataAsBytes);
		} else {
			this.debugData = null;
		}

	}

	@Override
	public String describePayload() {
		StringBuilder describe = new StringBuilder();
		describe.append("Last-Stream-ID=" + getLastStreamId()).append(keyValSep);
		describe.append("Error-Code=" + getErrorCode()).append(keyValSep);
		describe.append("Debug-Data=" + getDebugData());
		return describe.toString();
	}

	public int getLastStreamId() {
		return lastStreamId;
	}

	public ErrorCodeRegistry getErrorCode() {
		return errorCode;
	}

	public String getDebugData() {
		return debugData;
	}

}
