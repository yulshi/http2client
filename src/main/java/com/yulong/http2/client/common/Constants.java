package com.yulong.http2.client.common;

public class Constants {

	public static final String PRE_PREFACE_HEX = "505249202a20485454502f322e300d0a0d0a534d0d0a0d0a";
	public static final byte[] EMPTY_PING = new byte[8];
	public static final int CONNECTION_STREAM_ID = 0;
	public static final int HTTP_UPGRADE_STREAM_ID = 1;
	public static final String HTTP_UPGRADE_SETTINGS_HEADER = "HTTP2-Settings";
	public static final String HTTP_UPGRADE_PROTOCOL_NAME = "h2c";

}
