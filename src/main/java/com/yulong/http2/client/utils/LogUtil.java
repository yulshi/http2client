package com.yulong.http2.client.utils;

import static com.yulong.http2.client.utils.Utils.toHexString;
import static com.yulong.http2.client.utils.Utils.showPartOfTextIfTooLong;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import io.netty.buffer.ByteBuf;
import com.yulong.http2.client.frame.Frame;

public class LogUtil {

	private static final SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss:SSS] ");
	private static final boolean timestampNeeded = Boolean.getBoolean("http2.debug.timestamp");

	public static void log(ByteBuf msg) {
		byte[] bytes = new byte[msg.readableBytes()];
		msg.getBytes(msg.readerIndex(), bytes);
		System.out
				.println((timestampNeeded ? sdf.format(new Date()) : "") + "<< " + showPartOfTextIfTooLong(toHexString(bytes)));
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
		StringBuilder sb = new StringBuilder();
		if (textMsg.contains("\n")) {
			StringTokenizer st = new StringTokenizer(textMsg, "\n");
			while (st.hasMoreTokens()) {
				if (sb.length() != 0) {
					sb.append("\t");
				}
				sb.append(st.nextToken()).append("\n");
			}
			//textMsg = "****\n" + textMsg;
		} else {
			sb.append(textMsg);
		}
		System.out.println((timestampNeeded ? sdf.format(new Date()) : "") + sb.toString());
	}

}
