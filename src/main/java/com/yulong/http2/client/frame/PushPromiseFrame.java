package com.yulong.http2.client.frame;

import static com.yulong.http2.client.frame.FrameType.PUSH_PROMISE;
import static com.yulong.http2.client.utils.Utils.combine;
import static com.yulong.http2.client.utils.Utils.createBinaryData;
import static com.yulong.http2.client.utils.Utils.fromInt;

import java.nio.ByteBuffer;

import com.yulong.http2.client.utils.Utils;

public class PushPromiseFrame extends Frame implements Continuable, Padded {

	private final boolean endHeaders;
	private final boolean padded;

	private final int padLength;
	private final int promisedStreamId;
	private final byte[] headerBlockFragment;
	private final byte[] padding;

	public PushPromiseFrame(int streamId, boolean endHeaders, int promisedStreamId, final byte[] headerBlockFragment) {
		this(promisedStreamId, endHeaders, false, -1, promisedStreamId, headerBlockFragment);
	}

	public PushPromiseFrame(int streamId, boolean endHeaders, boolean padded, int padLength, int promisedStreamId,
			final byte[] headerBlockFragment) {

		this.endHeaders = endHeaders;
		this.padded = padded;

		this.padLength = padLength;
		this.promisedStreamId = promisedStreamId;
		this.headerBlockFragment = headerBlockFragment;
		if (!padded) {
			this.padding = new byte[0];
		} else {
			this.padding = createBinaryData(padLength, new byte[] { (byte) 0 });
		}

		byte flags = (byte) (this.endHeaders ? 0x4 : 0x0);
		flags |= (this.padded ? 0x8 : 0x0);
		flags &= 0xff;

		byte[] payload = null;
		if (this.padded) {
			payload = combine(new byte[] { (byte) this.padLength }, fromInt(this.promisedStreamId, 4),
					this.headerBlockFragment, this.padding);
		} else {
			payload = combine(fromInt(this.promisedStreamId, 4), this.headerBlockFragment);
		}

		init(payload.length, PUSH_PROMISE, flags, streamId, payload);

	}

	public PushPromiseFrame(Frame rawFrame) {

		copyFrom(rawFrame);

		this.endHeaders = (getFlags() & 0x4) == 0x4;
		this.padded = (getFlags() & 0x8) == 0x8;

		ByteBuffer payloadBuff = ByteBuffer.wrap(getPayload());

		if (this.padded) {
			this.padLength = payloadBuff.get();
		} else {
			this.padLength = -1;
		}

		this.promisedStreamId = payloadBuff.getInt();

		this.headerBlockFragment = new byte[getPayloadLength() - (this.padLength + 1) - 4];
		payloadBuff.get(this.headerBlockFragment);

		if (this.padLength > 0) {
			this.padding = new byte[this.padLength];
			payloadBuff.get(this.padding);
		} else {
			this.padding = new byte[0];
		}
	}

	@Override
	public boolean isEndHeaders() {
		return endHeaders;
	}

	@Override
	public boolean isPadded() {
		return padded;
	}

	@Override
	public int getPadLength() {
		return padLength;
	}

	public int getPromisedStreamId() {
		return promisedStreamId;
	}

	@Override
	public byte[] getHeaderBlockFragment() {
		return headerBlockFragment;
	}

	@Override
	public byte[] getPadding() {
		return padding;
	}

	@Override
	public String describeFlags() {
		StringBuilder describe = new StringBuilder();
		describe.append("END_HEADERS=" + isEndHeaders()).append(keyValSep);
		describe.append("PADDED=" + isPadded());
		return describe.toString();
	}

	@Override
	public String describePayload() {

		if (padded) {
			StringBuilder describe = new StringBuilder();
			describe.append("Pad_Length=" + getPadLength()).append(keyValSep);
			describe.append("Promised-Stream-ID=").append(promisedStreamId).append(keyValSep);
			describe.append("Header-Block-Fragment=" + Utils.toHexString(getHeaderBlockFragment())).append(keyValSep);
			describe.append("Padding=" + Utils.toHexString(getPadding()));
			return describe.toString();
		} else {
			StringBuilder describe = new StringBuilder();
			describe.append("Promised-Stream-ID=").append(promisedStreamId).append(keyValSep);
			describe.append("Header-Block-Fragment=" + Utils.toHexString(getHeaderBlockFragment()));
			return describe.toString();
		}

	}

}
