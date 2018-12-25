package com.yulong.http2.client.netty;

import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;

import java.util.List;

import com.yulong.http2.client.ConnectionConfig;
import com.yulong.http2.client.message.Header;

/**
 * Send a HTTP/1.1 request and upgrade it to HTTP/2
 */
public class Http2UpgradeInitializer extends Http2Initializer {

	private final String host;
	private final int port;
	private final String method;
	private final String path;
	private final List<Header> headers;
	private final String requestBody;
	private final EventLoopGroup eventLoopGroup;

	public Http2UpgradeInitializer(String host, int port, String method, String path, List<Header> headers,
			String requestBody, ConnectionConfig config, EventLoopGroup eventLoopGroup) {
		super(config);
		this.host = host;
		this.port = port;
		this.method = method;
		this.path = path;
		this.headers = headers;
		this.requestBody = requestBody;
		this.eventLoopGroup = eventLoopGroup;
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
				requestBody, config.getConnectionPreface());
		config.setConnectionPreface(null);
		this.frameHandler = new Http2FrameHandler(http2InUsePromise, host, port, "http", config, eventLoopGroup);
		ch.pipeline().addLast(upgradeHandler);
	}

}
