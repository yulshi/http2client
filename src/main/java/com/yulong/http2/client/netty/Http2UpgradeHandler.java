package com.yulong.http2.client.netty;

import static com.yulong.http2.client.utils.Debug.debugUpgrade;
import static com.yulong.http2.client.utils.LogUtil.log;
import static com.yulong.http2.client.utils.Utils.bytes2String;
import static com.yulong.http2.client.utils.Utils.toHexString;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;
import com.yulong.http2.client.Http2StartingException;
import com.yulong.http2.client.message.Header;

/**
 * A handler to send upgrade requst and analyze the response.
 */
public class Http2UpgradeHandler extends ByteToMessageDecoder {

	private static final String LINE_SEP = "\r\n";

	private ChannelPromise promise;
	private final String host;
	private final int port;
	private final String method;
	private final String path;
	private final List<Header> headers;
	private final String requestBody;
	private final byte[] connectionPreface;

	private boolean shouldContinueRead = true;

	public Http2UpgradeHandler(ChannelPromise promise, String host, int port, String method, String path,
			List<Header> headers, String requestBody, byte[] connectionPreface) {
		this.promise = promise;
		this.host = host;
		this.port = port;
		this.method = method;
		this.path = path;
		this.headers = headers;
		this.requestBody = requestBody;
		this.connectionPreface = connectionPreface;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		final ChannelFuture f = ctx.writeAndFlush(encodeUpgradeRequest());
		f.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				//log("Upgrade request is sent!");
			}
		});
		ctx.fireChannelActive();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace(System.out);
		ctx.close();
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

		log(in);

		if (!shouldContinueRead) {
			return;
		}

		if (in.readableBytes() < 4) {
			return;
		}

		int index = 0;
		while (true) {
			byte[] lineBreak = new byte[4];
			in.getBytes(index, lineBreak);
			if (bytes2String(lineBreak).equals(LINE_SEP + LINE_SEP)) {
				break;
			}
			index++;
		}
		index += 4;

		byte[] responseAsBytes = new byte[index];
		in.readBytes(responseAsBytes);
		String responseAsString = bytes2String(responseAsBytes);

		debugUpgrade(responseAsString);

		if (responseAsString.length() < 12) {
			ctx.close();
			return;
		}

		String statusCode = responseAsString.substring(9, 12);
		if (!statusCode.equals("101")) {
			this.promise.setFailure(new Http2StartingException("Unexpected status code: " + statusCode));
			ctx.close();
			shouldContinueRead = false;
			return;
		}

		log("Upgrade Succeeds!");

		if (connectionPreface != null) {
			log("Sending connection preface: " + toHexString(connectionPreface));
			ctx.writeAndFlush(Unpooled.copiedBuffer(connectionPreface));
		}

		ctx.pipeline().remove(this);

	}

	/**
	 * Encode the upgrade request
	 * 
	 * @return
	 */
	private ByteBuf encodeUpgradeRequest() {

		StringBuilder sb = new StringBuilder();

		sb.append(method).append(" ").append(path).append(" HTTP/1.1").append(LINE_SEP);
		sb.append("HOST: " + host + ":" + port).append(LINE_SEP);

		for (Header header : headers) {
			sb.append(header.getName()).append(": ").append(header.getValue()).append(LINE_SEP);
		}
		sb.append(LINE_SEP);

		if (requestBody != null) {
			sb.append(requestBody);
		}

		debugUpgrade(sb.toString());

		return Unpooled.copiedBuffer(sb, CharsetUtil.UTF_8);

	}

}
