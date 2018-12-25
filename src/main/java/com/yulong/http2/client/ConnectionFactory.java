package com.yulong.http2.client;

import static com.yulong.http2.client.common.Constants.HTTP_UPGRADE_PROTOCOL_NAME;
import static com.yulong.http2.client.common.Constants.HTTP_UPGRADE_SETTINGS_HEADER;
import static com.yulong.http2.client.utils.LogUtil.log;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import com.yulong.http2.client.Connection.StartBy;
import com.yulong.http2.client.common.SettingsRegistry;
import com.yulong.http2.client.frame.SettingsFrame;
import com.yulong.http2.client.message.Header;
import com.yulong.http2.client.netty.Http2AlpnInitializer;
import com.yulong.http2.client.netty.Http2FrameHandler;
import com.yulong.http2.client.netty.Http2Initializer;
import com.yulong.http2.client.netty.Http2PriorKnowledgeInitializer;
import com.yulong.http2.client.netty.Http2UpgradeInitializer;

public class ConnectionFactory {

	private final ConnectionConfig config;

	public ConnectionFactory() {
		this(new ConnectionConfig());
	}

	public ConnectionFactory(ConnectionConfig config) {
		this.config = config;
	}

	public ConnectionFactory(byte[] connectionPreface) {
		this(new ConnectionConfig().setConnectionPreface(connectionPreface));
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
	public Connection create(StartBy startBy, String host, int port) throws Http2StartingException {
		return create(startBy, host, port, null, null, null, null);
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
	public Connection create(StartBy startBy, String host, int port, String method, String path, List<Header> headers,
			String requestBody) throws Http2StartingException {

		EventLoopGroup workerGroup = new NioEventLoopGroup();

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
				SettingsFrame settingsFrame = new SettingsFrame(new TreeMap<SettingsRegistry, Integer>() {
					{
						put(SettingsRegistry.MAX_CONCURRENT_STREAMS, 100);
					}
				});
				headers.add(new Header(HTTP_UPGRADE_SETTINGS_HEADER, settingsFrame.toUrlEncoded()));
			}
			initializer = new Http2UpgradeInitializer(host, port, method, path, headers, requestBody, config, workerGroup);
			break;
		case alpn:
			initializer = new Http2AlpnInitializer(host, port, config, workerGroup);
			break;
		case prior_knowledge:
			initializer = new Http2PriorKnowledgeInitializer(host, port, config, workerGroup);
			break;
		}

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
			frameHandler.awaitConfirmation(5L, TimeUnit.SECONDS);
			return frameHandler;
		}

	}

}
