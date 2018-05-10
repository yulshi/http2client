package com.yulong.http2.client.frame;

import static com.yulong.http2.client.frame.FrameType.RST_STREAM;
import static com.yulong.http2.client.utils.Utils.fromInt;
import static com.yulong.http2.client.utils.Utils.toInt;

import com.yulong.http2.client.common.ErrorCodeRegistry;

public class ResetFrame extends Frame {

	public static final int PAYLOAD_LENGTH = 4;

	private final ErrorCodeRegistry errorCode;

	public ResetFrame(int streamId, ErrorCodeRegistry errorCode) {
		this.errorCode = errorCode;
		init(PAYLOAD_LENGTH, RST_STREAM, (byte) 0, streamId, fromInt(errorCode.getCode(), PAYLOAD_LENGTH));
	}

	public ResetFrame(Frame rawFrame) {
		copyFrom(rawFrame);
		this.errorCode = ErrorCodeRegistry.from(toInt(rawFrame.getPayload()));
	}

	public ErrorCodeRegistry getErrorCode() {
		return errorCode;
	}

	@Override
	public String describePayload() {
		return "Error-Code=" + errorCode;
	}

}
