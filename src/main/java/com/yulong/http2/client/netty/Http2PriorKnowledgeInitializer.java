package com.yulong.http2.client.netty;

import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import com.yulong.http2.client.ConnectionConfig;
import com.yulong.http2.client.Http2StartingException;

/**
 * Send a connection preface directly after the http connection is established.
 */
public class Http2PriorKnowledgeInitializer extends Http2Initializer {

	private final String host;
	private final int port;
	private final EventLoopGroup eventLoopGroup;

	public Http2PriorKnowledgeInitializer(String host, int port, final ConnectionConfig config,
			EventLoopGroup eventLoopGroup) throws Http2StartingException {
		super(config);
		this.host = host;
		this.port = port;
		this.eventLoopGroup = eventLoopGroup;
	}

	/**
	 * To initialize the Http2FrameHandler instance
	 * 
	 * @param ch
	 */
	@Override
	protected void configure(SocketChannel ch) {
		ChannelPromise http2InUsePromise = ch.newPromise();
		this.frameHandler = new Http2FrameHandler(http2InUsePromise, host, port, "http", config, eventLoopGroup);
	}

}
