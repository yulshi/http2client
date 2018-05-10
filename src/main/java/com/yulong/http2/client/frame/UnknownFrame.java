package com.yulong.http2.client.frame;

public class UnknownFrame extends Frame {

	public UnknownFrame(Frame rawFrame) {
		copyFrom(rawFrame);
	}

}
