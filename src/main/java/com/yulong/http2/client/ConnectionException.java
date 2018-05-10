package com.yulong.http2.client;

import com.yulong.http2.client.common.ErrorCodeRegistry;

public class ConnectionException extends Exception {

	private final ErrorCodeRegistry error;

	public ConnectionException(ErrorCodeRegistry error) {
		this.error = error;
	}

	public ConnectionException(ErrorCodeRegistry error, String message) {
		super(message);
		this.error = error;
	}

	public ConnectionException(ErrorCodeRegistry error, String message, Throwable cause) {
		super(message, cause);
		this.error = error;
	}

	public ErrorCodeRegistry GetError() {
		return error;
	}

	@Override
	public String toString() {
		StringBuilder describe = new StringBuilder();
		describe.append(this.getClass().getSimpleName()).append(": ").append(error.name()).append("; ")
				.append(this.getMessage());
		return describe.toString();
	}

}
