package com.yulong.http2.client.utils;

import static com.yulong.http2.client.utils.Utils.toHexString;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.yulong.http2.client.frame.Frame;

import io.netty.buffer.ByteBuf;

public class LogUtil {

	private static final SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss:SSS] ");
	private static final boolean timestampNeeded = false;

	public static void log(ByteBuf msg) {
		byte[] bytes = new byte[msg.readableBytes()];
		msg.getBytes(msg.readerIndex(), bytes);
		System.out.println(timestampNeeded ? sdf.format(new Date()) : "" + "<< " + toHexString(bytes));
	}

	public static void log(List<Frame> frames) {
		StringBuilder sb = new StringBuilder();
		sb.append("{").append("\n");
		for (Frame frame : frames) {
			sb.append("\t").append(frame).append("\n");
		}
		sb.append("}");
		System.out.println(sb.toString());
	}

	public static void log(Object msg) {
		String textMsg = msg.toString();
		if (textMsg.contains("\n")) {
			textMsg = "\n" + textMsg;
		}
		System.out.println(timestampNeeded ? sdf.format(new Date()) : "" + textMsg);
	}

}
