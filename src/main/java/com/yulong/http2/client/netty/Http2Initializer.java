package com.yulong.http2.client.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import com.yulong.http2.client.ConnectionConfig;

/**
 * An template for an intializer
 */
public abstract class Http2Initializer extends ChannelInitializer<SocketChannel> {

	protected Http2FrameHandler frameHandler;
	protected final ConnectionConfig config;

	public Http2Initializer(ConnectionConfig config) {
		this.config = config;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		configure(ch);
		configureEndOfPipeline(ch);
	}

	/**
	 * Return the main handler which is also a connection implementation
	 * 
	 * @return
	 */
	public Http2FrameHandler getFrameHandler() {
		return frameHandler;
	}

	/**
	 * Add handlers to the end of pipeline
	 * 
	 * @param ch
	 */
	private void configureEndOfPipeline(SocketChannel ch) {
		ch.pipeline().addLast(new Http2FrameDecoder());
		ch.pipeline().addLast(frameHandler);
	}

	/**
	 * To initialize the Http2FrameHandler instance and add appropriate handlers
	 * to the pipeline.
	 * 
	 * @param ch
	 */
	protected abstract void configure(SocketChannel ch);

}
