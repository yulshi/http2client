package com.yulong.http2.client;

import com.yulong.http2.client.common.ErrorCodeRegistry;

public class StreamException extends ConnectionException {

	private final int streamId;

	public StreamException(int streamId, ErrorCodeRegistry error) {
		super(error);
		this.streamId = streamId;
	}

	public StreamException(int streamId, ErrorCodeRegistry error, String message) {
		super(error, message);
		this.streamId = streamId;
	}

	public StreamException(int streamId, ErrorCodeRegistry error, String message, Throwable cause) {
		super(error, message, cause);
		this.streamId = streamId;
	}

	public int getStreamId() {
		return streamId;
	}

	@Override
	public String toString() {
		StringBuilder describe = new StringBuilder();
		describe.append(this.getClass().getSimpleName()).append(": ").append(streamId).append("; ")
				.append(super.GetError().name()).append("; ").append(this.getMessage());
		return describe.toString();
	}

}
