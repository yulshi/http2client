package com.yulong.http2.client.frame;

public interface Padded {

	int getPayloadLength();

	boolean isPadded();

	int getPadLength();

	byte[] getPadding();

}
