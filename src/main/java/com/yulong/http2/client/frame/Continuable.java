package com.yulong.http2.client.frame;

public interface Continuable {

	public boolean isEndHeaders();

	public byte[] getHeaderBlockFragment();

}
