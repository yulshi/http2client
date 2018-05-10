package com.yulong.http2.client.netty;

import com.yulong.http2.client.Http2StartingException;

import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;

/**
 * Send a connection preface directly after the http connection is established.
 * 
 * @author yushi
 * @since Jan.15 2016
 */
public class Http2PriorKnowledgeInitializer extends Http2Initializer {

	public Http2PriorKnowledgeInitializer(final byte[] connectionPreface) throws Http2StartingException {
		super(connectionPreface);
	}

	/**
	 * To initialize the Http2FrameHandler instance
	 * 
	 * @param ch
	 */
	@Override
	protected void configure(SocketChannel ch) {
		ChannelPromise http2InUsePromise = ch.newPromise();
		this.frameHandler = new Http2FrameHandler(http2InUsePromise, connectionPreface);
	}

}
