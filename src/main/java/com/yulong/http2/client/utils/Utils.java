package com.yulong.http2.client.utils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilities
 * 
 * @author Yulong Shi(yu.long.shi@oracle.com)
 * @since 4/9/2012 12c
 *
 */
public final class Utils {

	/**
	 * To convert a byte to hex code representation.
	 * 
	 * @param b
	 * @return
	 */
	public static String toHexString(byte b) {
		char hexDigit[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
		char[] array = { hexDigit[(b >> 4) & 0x0f], hexDigit[b & 0x0f] };
		return new String(array);
	}

	/**
	 * To convert a byte array to hex code representation.
	 * 
	 * @param bytes
	 * @return
	 */
	public static String toHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		if (bytes != null) {
			for (byte b : bytes) {
				sb.append(toHexString(b));
			}
		}
		return sb.toString();
	}

	/**
	 * To convert the specified byte array into a UTF-8 encoded text
	 * representation.
	 * 
	 * @param bytes
	 * @return
	 */
	public static String bytes2String(byte[] bytes) {
		return bytes2String(bytes, "UTF-8");
	}

	/**
	 * To convert the specified byte array into a string with the specified
	 * charset.
	 * 
	 * @param bytes
	 * @param charset
	 * @return
	 */
	public static String bytes2String(byte[] bytes, String charset) {
		if (bytes == null) {
			return null;
		}
		String res = null;
		try {
			res = new String(bytes, charset);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return res;
	}

	/**
	 * To convert the specified string into a byte array with UTF-8.
	 * 
	 * @param str
	 * @return
	 */
	public static byte[] string2Bytes(String str) {
		return string2Bytes(str, "UTF-8");
	}

	/**
	 * To convert the specified string into a byte array with given charset.
	 * 
	 * @param str
	 * @param charset
	 * @return
	 */
	public static byte[] string2Bytes(String str, String charset) {
		if (str == null) {
			return null;
		}
		byte[] bytes = null;
		try {
			bytes = str.getBytes(charset);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return bytes;
	}

	/**
	 * To combine two byte array into one byte array.
	 * 
	 * @param data1
	 * @param data2
	 * @return
	 */
	public static byte[] combine(byte[]... datas) {

		int totalSize = 0;
		for (byte[] data : datas) {
			totalSize += data.length;
		}
		ByteBuffer buff = ByteBuffer.allocate(totalSize);
		for (byte[] data : datas) {
			if (data.length > 0) {
				buff.put(data);
			}
		}
		return buff.array();

	}

	public static List<byte[]> fragment(byte[] block, int sizeOfEachFragment) {

		List<byte[]> res = new ArrayList<>();

		ByteBuffer byteBuff = ByteBuffer.wrap(block);

		while (byteBuff.remaining() > sizeOfEachFragment) {
			byte[] fragment = new byte[sizeOfEachFragment];
			byteBuff.get(fragment);
			res.add(fragment);
		}

		if (byteBuff.remaining() > 0) {
			byte[] fragment = new byte[byteBuff.remaining()];
			byteBuff.get(fragment);
			res.add(fragment);
		}

		return res;
	}

	/**
	 * To convert the specified bytes into an integer value.
	 * 
	 * @param buff
	 * @param position
	 * @param length
	 * @return
	 */
	public static int toInt(ByteBuffer buff, int position, int length) {
		buff.position(position);
		byte[] bytes = new byte[length];
		buff.get(bytes);
		return toInt(bytes);
	}

	/**
	 * To convert the specified byte array into an integer value.
	 * 
	 * @param srcBytes
	 * @return
	 */
	public static int toInt(byte[] srcBytes) {
		byte[] bytes = new byte[4];
		System.arraycopy(srcBytes, 0, bytes, 4 - srcBytes.length, srcBytes.length);
		ByteBuffer buff = ByteBuffer.wrap(bytes);
		return buff.getInt();
	}

	/**
	 * To convert the specified bytes into a long value.
	 * 
	 * @param buff
	 *            the source byte buffer.
	 * @param position
	 *            from where to convert
	 * @param length
	 *            the length to convert
	 * @return a long value resulted from the byte array.
	 */
	public static long toLong(ByteBuffer buff, int position, int length) {
		buff.position(position);
		byte[] bytes = new byte[length];
		buff.get(bytes);
		return toLong(bytes);
	}

	/**
	 * To convert the specified bytes into a long value.
	 * 
	 * @param srcBytes
	 * @return
	 */
	public static long toLong(byte[] srcBytes) {
		byte[] bytes = new byte[8];
		System.arraycopy(srcBytes, 0, bytes, 8 - srcBytes.length, srcBytes.length);
		ByteBuffer buff = ByteBuffer.wrap(bytes);
		return buff.getLong();
	}

	public static byte[] fromInt(int integer, int expectedLength) {
		byte[] result = new byte[expectedLength];
		for (int i = 0; i < result.length; i++) {
			result[i] = (byte) (integer >> ((result.length - 1 - i) * 8));
		}
		return result;
	}

	/**
	 * Split the give string into parts according to the give size.
	 * 
	 * @param textData
	 * @param size
	 * @return
	 */
	public static List<String> split(String textData, int size) {

		int length = textData.length();

		List<String> list = new ArrayList<String>();

		if (size < 0) {
			list.add(textData);
		} else {
			int beginIndex = -1;
			int endIndex = 0;

			while (endIndex <= length) {
				beginIndex = endIndex;
				int deta = length - endIndex;
				if (deta == 0) {
					break;
				}
				if (deta > size) {
					endIndex += size;
				} else {
					endIndex += deta;
				}
				list.add(textData.substring(beginIndex, endIndex));
			}
		}
		return list;

	}

	/**
	 * Convert a string array to a string seperated by comma.
	 * 
	 * @param arr
	 * @return
	 */
	public static String array2String(String[] arr) {
		String res = null;
		for (int i = 0; i < arr.length; i++) {
			if (i == 0) {
				res = arr[i];
			} else {
				res += ", " + arr[i];
			}
		}
		return res;
	}

	/**
	 * To get byte array from the given hex code string.
	 * 
	 * @param hexCodeStr
	 * @return
	 */
	public static byte[] fromHexString(String hexCodeStr) {

		if (hexCodeStr.length() % 2 != 0) {
			return new byte[] {};
		}

		byte[] res = new byte[hexCodeStr.length() / 2];

		for (int i = 0; i < hexCodeStr.length(); i += 2) {
			String temp = hexCodeStr.substring(i, i + 2);
			res[i / 2] = (byte) Integer.parseInt(temp, 16);
		}

		return res;

	}

	/**
	 * Utitily for printing partial message.
	 * 
	 * @param text
	 * @return
	 */
	public static String showPartOfTextIfTooLong(String text) {
		int length = 20;
		if (text != null && text.length() > length) {
			return text.substring(0, length) + "... (length:" + text.length() + ")";
		} else {
			return text;
		}
	}

	/**
	 * To sleep for the specified seconds.
	 * 
	 * @param secs
	 */
	public static void sleepSecs(int secs) {
		try {
			Thread.sleep(secs * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create a text message with the base string and the given required length.
	 * 
	 * @param length
	 * @param base
	 * @return
	 */
	public static String createLongMessage(int length, String base) {

		int times = length / base.length();
		int mod = length % base.length();

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < times; i++) {
			sb.append(base);
		}
		if (mod != 0) {
			sb.append(base.substring(0, mod));
		}
		return sb.toString();

	}

	/**
	 * Create a binary array acorrding to the given length and base array.
	 * 
	 * @param length
	 * @param base
	 * @return
	 */
	public static byte[] createBinaryData(int length, byte[] base) {

		int times = length / base.length;
		int mod = length % base.length;

		ByteBuffer buff = ByteBuffer.allocate(length);
		for (int i = 0; i < times; i++) {
			buff.put(base);
		}
		if (mod != 0) {
			buff.put(Arrays.copyOf(base, mod));
		}
		return buff.array();

	}

	public static void main(String[] args) {

		byte[] block = createBinaryData(20, new byte[] { (byte) 0xef });
		List<byte[]> fragments = fragment(block, 5);

		for (byte[] fragment : fragments) {
			System.out.println(toHexString(fragment));
		}
	}

}
