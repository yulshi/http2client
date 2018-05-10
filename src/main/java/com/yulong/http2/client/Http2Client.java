package com.yulong.http2.client;

import static com.yulong.http2.client.common.Constants.HTTP_UPGRADE_PROTOCOL_NAME;
import static com.yulong.http2.client.common.Constants.HTTP_UPGRADE_SETTINGS_HEADER;
import static com.yulong.http2.client.utils.LogUtil.log;
import static com.yulong.http2.client.utils.Utils.bytes2String;
import static com.yulong.http2.client.utils.Utils.combine;
import static com.yulong.http2.client.utils.Utils.fromHexString;
import static java.util.Base64.getUrlEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.yulong.http2.client.Connection.StartBy;
import com.yulong.http2.client.common.Constants;
import com.yulong.http2.client.frame.SettingsFrame;
import com.yulong.http2.client.message.Header;
import com.yulong.http2.client.netty.Http2AlpnInitializer;
import com.yulong.http2.client.netty.Http2FrameHandler;
import com.yulong.http2.client.netty.Http2Initializer;
import com.yulong.http2.client.netty.Http2PriorKnowledgeInitializer;
import com.yulong.http2.client.netty.Http2UpgradeInitializer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class Http2Client {

	private boolean sendAcknowledgePrefaceImmediately = true;

	public Http2Client() {
		this(true);
	}

	public Http2Client(boolean sendAcknowledgePrefaceImmediately) {
		this.sendAcknowledgePrefaceImmediately = sendAcknowledgePrefaceImmediately;
	}

	/**
	 * Opean an HTTP/2 connection according to the given StartBy method
	 * 
	 * If it's by upgrade, an "OPTIONS" request is sent to perform the upgrade.
	 * The connection preface is followed by an empty SETTINGS payload
	 * 
	 * @param startBy
	 * @param host
	 * @param port
	 * @return
	 * @throws Http2StartingException
	 */
	public Connection openConnection(StartBy startBy, String host, int port) throws Http2StartingException {
		return openConnection(startBy, host, port, createConnectionPreface(), null, null, null, null);
	}

	/**
	 * Opean an HTTP/2 connection according to the given StartBy method
	 * 
	 * If it's by upgrade, an "OPTIONS" request is sent to perform the upgrade
	 * 
	 * @param startBy
	 * @param host
	 * @param port
	 * @param connectionPreface
	 * @return
	 * @throws Http2StartingException
	 */
	public Connection openConnection(StartBy startBy, String host, int port, byte[] connectionPreface)
			throws Http2StartingException {
		return openConnection(startBy, host, port, connectionPreface, null, null, null, null);
	}

	/**
	 * Opean an HTTP/2 connection according to the given StartBy method. User can provide
	 * arbitrary values for method, path, header and requestBody
	 * 
	 * @param host
	 * @param port
	 * @param method
	 * @param path
	 * @param headers
	 * @param requestBody
	 * @param connectionPreface
	 * @return
	 * @throws Http2StartingException
	 */
	public Connection openConnection(StartBy startBy, String host, int port, byte[] connectionPreface, String method,
			String path, List<Header> headers, String requestBody) throws Http2StartingException {

		Http2Initializer initializer = null;
		switch (startBy) {
		case upgrade:
			if (method == null) {
				method = "OPTIONS";
				requestBody = null;
			}
			if (path == null) {
				path = "*";
			}
			if (headers == null) {
				headers = new ArrayList<Header>();
				headers.add(new Header("Connection", "Upgrade, " + HTTP_UPGRADE_SETTINGS_HEADER));
				headers.add(new Header("Upgrade", HTTP_UPGRADE_PROTOCOL_NAME));
				headers.add(new Header(HTTP_UPGRADE_SETTINGS_HEADER, urlEncodeSettingsPayload(SettingsFrame.EMPTY)));
			}
			initializer = new Http2UpgradeInitializer(host, port, method, path, headers, requestBody, connectionPreface);
			break;
		case alpn:
			initializer = new Http2AlpnInitializer(connectionPreface);
			break;
		case prior_knowledge:
			initializer = new Http2PriorKnowledgeInitializer(connectionPreface);
			break;
		}

		EventLoopGroup workerGroup = new NioEventLoopGroup();

		Bootstrap b = new Bootstrap();
		b.group(workerGroup).channel(NioSocketChannel.class).handler(initializer).option(ChannelOption.SO_KEEPALIVE, true);

		ChannelFuture f = b.connect(host, port);
		if (!f.awaitUninterruptibly(5, TimeUnit.SECONDS)) {
			throw new Http2StartingException(String.format("Timed out waiting for connecting to %s:%d", host, port));
		}

		assert f.isDone();

		if (f.isCancelled()) {
			throw new Http2StartingException("Connection attempt cancelled by user");
		} else if (!f.isSuccess()) {
			throw new Http2StartingException(String.format("Failed to connect to %s:%d", host, port), f.cause());
		} else {
			log(String.format("Connected to %s:%d ...", host, port));
			Http2FrameHandler frameHandler = initializer.getFrameHandler();
			frameHandler.setSendAcknowledgePrefaceImmediately(sendAcknowledgePrefaceImmediately);
			frameHandler.awaitConfirmation(5L, TimeUnit.SECONDS);
			return frameHandler;
		}

	}

	/**
	 * Create a connection preface with empty SETTINGS payload
	 * 
	 * @return
	 */
	public byte[] createConnectionPreface() {
		return createConnectionPreface(SettingsFrame.EMPTY);
	}

	/**
	 * Create a connection preface base on the given SETTINGS frame
	 * 
	 * @param settingsFrame
	 * @return
	 */
	public byte[] createConnectionPreface(SettingsFrame settingsFrame) {
		return combine(fromHexString(Constants.PRE_PREFACE_HEX), settingsFrame.asBytes());
	}

	/**
	 * Get base64url encoding of SETTINGS frame payload
	 * 
	 * @param settingsFrame
	 * @return
	 */
	public String urlEncodeSettingsPayload(SettingsFrame settingsFrame) {
		return bytes2String(getUrlEncoder().encode(settingsFrame.getPayload()));
	}

}
