package com.yulong.http2.client.netty;

import javax.net.ssl.SSLException;

import com.yulong.http2.client.Http2StartingException;

import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Send a connection preface directly after the https connection is established.
 * 
 * @author yushi
 * @since Jan.17 2018
 */
public class Http2AlpnInitializer extends Http2Initializer {

	private final SslContext sslCtx;

	public Http2AlpnInitializer(final byte[] connectionPreface) throws Http2StartingException {
		super(connectionPreface);
		SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
		try {
			sslCtx = SslContextBuilder.forClient().sslProvider(provider)
					/* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
					 * Please refer to the HTTP/2 specification for cipher requirements. */
					.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
					.trustManager(InsecureTrustManagerFactory.INSTANCE)
					.applicationProtocolConfig(new ApplicationProtocolConfig(
							Protocol.ALPN,
							// NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
							SelectorFailureBehavior.NO_ADVERTISE,
							// ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
							SelectedListenerFailureBehavior.ACCEPT, 
							ApplicationProtocolNames.HTTP_2,
							ApplicationProtocolNames.HTTP_1_1))
					.build();
		} catch (SSLException e) {
			throw new Http2StartingException("Failed to get SSL context", e);
		}
	}

	/**
	 * To initialize the Http2FrameHandler instance and add SslHandler to the pipeline
	 * 
	 * @param ch
	 */
	@Override
	protected void configure(SocketChannel ch) {
		ChannelPromise http2InUsePromise = ch.newPromise();
		this.frameHandler = new Http2FrameHandler(http2InUsePromise, connectionPreface);
		ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
	}

}
