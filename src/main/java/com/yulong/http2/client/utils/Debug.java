package com.yulong.http2.client.utils;

import static com.yulong.http2.client.utils.LogUtil.log;

import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;
import com.yulong.http2.client.common.FlowControlWindow;
import com.yulong.http2.client.message.Http2Headers;
import com.yulong.http2.client.message.Http2Response;
import com.yulong.http2.client.message.PushRequest;

public class Debug {

	private static boolean debugEnabled = Boolean.getBoolean("http2.debug");

	public static void debugRequest(Http2Headers headers) {
		if (Boolean.getBoolean("http2.debug.request") || debugEnabled) {
			log("prepare to send the following requeset headers\n" + headers);
		}
	}

	public static void debugResponse(int streamId, Http2Response response) {
		if (Boolean.getBoolean("http2.debug.response") || debugEnabled) {
			if (streamId != 1) {
				log("received the following response\n" + response);
			}
		}
	}

	public static void debugPushRequest(PushRequest pushRequest) {
		if (Boolean.getBoolean("http2.debug.push") || debugEnabled) {
			log("received the following push request\n" + pushRequest);
		}
	}

	public static void debugFrame(Supplier<String> supplier) {
		if (Boolean.getBoolean("http2.debug.frame")) {
			log(supplier.get());
		}
	}

	public static void debugResponseHeader(Http2Headers headers) {
		if (Boolean.getBoolean("http2.debug.response.header")) {
			log("received the following response headers\n" + headers);
		}
	}

	public static void debugOctet(ByteBuf in) {
		if (Boolean.getBoolean("http2.debug.octet")) {
			log(in);
		}
	}

	public static void debugUpgrade(String str) {
		if (Boolean.getBoolean("http2.debug.upgrade")) {
			log("upgrade request/response\n" + str);
		}
	}

	public static void debugResponseCache(Supplier<String> supplier) {
		if (Boolean.getBoolean("http2.debug.response.cache")) {
			log("[Response Cache] " + supplier.get());
		}
	}

	public static void debugFlowControl(FlowControlWindow window, String info) {
		if (Boolean.getBoolean("http2.debug.flowcontrol")) {
			log(info + ": " + window);
		}
	}

}
