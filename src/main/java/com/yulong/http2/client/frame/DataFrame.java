package com.yulong.http2.client.frame;

import static com.yulong.http2.client.frame.FrameType.DATA;
import static com.yulong.http2.client.utils.Utils.combine;
import static com.yulong.http2.client.utils.Utils.createBinaryData;

import java.nio.ByteBuffer;

import com.yulong.http2.client.utils.Utils;

public final class DataFrame extends Frame implements Padded {

	private final boolean endStream;
	private final boolean padded;
	private final int padLength;
	private final byte[] data;
	private final byte[] padding;

	public DataFrame(int streamId, boolean endStream, final byte[] data) {
		this(streamId, endStream, false, data, -1);
	}

	public DataFrame(int streamId, boolean endStream, boolean padded, final byte[] data, int padLength) {

		this.data = data;
		this.endStream = endStream;
		this.padded = padded;
		this.padLength = padLength;

		byte[] payload = null;
		if (this.padded) {
			this.padding = createBinaryData(padLength, new byte[] { (byte) 0 });
			payload = combine(new byte[] { (byte) padLength }, data, this.padding);
		} else {
			this.padding = new byte[0];
			payload = data;
		}

		byte flags = (byte) (((endStream ? 0x1 : (byte) 0x0) | (padded ? 0x8 : 0x0)) & 0xff);

		init(payload.length, DATA, flags, streamId, payload);
	}

	public DataFrame(final Frame rawFrame) {

		copyFrom(rawFrame);

		this.endStream = ((getFlags() & 0x1) == 0x1);
		this.padded = ((getFlags() & 0x8) == 0x8);

		ByteBuffer byteBuff = ByteBuffer.wrap(getPayload());
		if (this.padded) {

			this.padLength = byteBuff.get();
			int actualDataLength = getPayloadLength() - this.padLength - 1;
			this.data = new byte[actualDataLength];
			byteBuff.get(this.data);

			this.padding = new byte[padLength];
			if (padLength != 0) {
				byteBuff.get(this.padding);
			}

		} else {
			this.padLength = -1;

			int payloadLength = getPayloadLength();
			this.data = new byte[payloadLength];
			byteBuff.get(this.data);

			this.padding = new byte[0];
		}

	}

	@Override
	public String describeFlags() {
		StringBuilder describe = new StringBuilder();
		describe.append("END_STREAM=").append(isEndStream()).append(keyValSep);
		describe.append("PADDED=").append(isPadded());
		return describe.toString();
	}

	@Override
	public String describePayload() {

		if (isPadded()) {
			StringBuilder describe = new StringBuilder();
			describe.append("Pad_Length=").append(getPadLength()).append(keyValSep);
			describe.append("DATA=" + Utils.showPartOfTextIfTooLong(Utils.toHexString(getData()))).append(keyValSep);
			describe.append("Padding=").append(Utils.showPartOfTextIfTooLong(Utils.toHexString(getPadding())));
			return describe.toString();
		} else {
			return "DATA=" + Utils.showPartOfTextIfTooLong(Utils.toHexString(getData()));
		}

	}

	public boolean isEndStream() {
		return endStream;
	}

	@Override
	public boolean isPadded() {
		return padded;
	}

	@Override
	public int getPadLength() {
		return padLength;
	}

	public byte[] getData() {
		return data;
	}

	@Override
	public byte[] getPadding() {
		return padding;
	}

}
