package com.yulong.http2.client.netty;

import static com.yulong.http2.client.utils.Debug.debugOctet;
import static com.yulong.http2.client.utils.LogUtil.log;
import static com.yulong.http2.client.utils.Utils.toInt;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import com.yulong.http2.client.ConnectionException;
import com.yulong.http2.client.frame.Frame;

/**
 * Decode the received bytes into Frame object
 */
public class Http2FrameDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws ConnectionException {

		debugOctet(in);

		if (in.readableBytes() < 9) {
			return;
		}

		int payloadLength = toInt(in.nioBuffer(), 0, 3);
		if (in.readableBytes() < 9 + payloadLength) {
			return;
		}

		byte[] bytes = new byte[9 + payloadLength];
		in.readBytes(bytes);
		Frame rawFrame = new Frame(bytes);
		out.add(rawFrame);

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log("Exception occured when parsing frame: " + cause);
		cause.printStackTrace(System.out);
		ctx.close();
	}

}
