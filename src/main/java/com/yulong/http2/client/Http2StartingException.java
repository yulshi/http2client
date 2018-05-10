package com.yulong.http2.client;

public class Http2StartingException extends Exception {

	public Http2StartingException(String message) {
		super(message);
	}

	public Http2StartingException(String message, Throwable cause) {
		super(message, cause);
	}

}
