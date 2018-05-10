package com.yulong.http2.client.netty;

import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;

import java.util.List;

import com.yulong.http2.client.message.Header;

/**
 * Send a HTTP/1.1 request and upgrade it to HTTP/2
 * 
 * @author yushi
 * @since Jan.11 2016
 */
public class Http2UpgradeInitializer extends Http2Initializer {

	private final String host;
	private final int port;
	private final String method;
	private final String path;
	private final List<Header> headers;
	private final String requestBody;

	public Http2UpgradeInitializer(String host, int port, String method, String path, List<Header> headers,
			String requestBody, byte[] connectionPreface) {
		super(connectionPreface);
		this.host = host;
		this.port = port;
		this.method = method;
		this.path = path;
		this.headers = headers;
		this.requestBody = requestBody;
	}

	/**
	 * To initialize the Http2FrameHandler instance and add Http2UpgradeHandler to the pipeline
	 * 
	 * @param ch 
	 */
	@Override
	protected void configure(SocketChannel ch) {
		ChannelPromise http2InUsePromise = ch.newPromise();
		Http2UpgradeHandler upgradeHandler = new Http2UpgradeHandler(http2InUsePromise, host, port, method, path, headers,
				requestBody, connectionPreface);
		this.frameHandler = new Http2FrameHandler(http2InUsePromise, null);
		ch.pipeline().addLast(upgradeHandler);
	}

}
