package com.yulong.http2.client.frame;

import static com.yulong.http2.client.frame.FrameType.HEADERS;
import static com.yulong.http2.client.utils.Utils.combine;
import static com.yulong.http2.client.utils.Utils.createBinaryData;
import static com.yulong.http2.client.utils.Utils.showPartOfTextIfTooLong;
import static com.yulong.http2.client.utils.Utils.toHexString;

import java.nio.ByteBuffer;

public class HeadersFrame extends Frame implements Continuable, Padded {

	// Flags:
	private final boolean endStream;
	private final boolean endHeaders;
	private final boolean padded;
	private final boolean priority;

	// Payload:
	private final int padLength;
	private final PriorityFrame priorityFrame;
	private final byte[] headerBlockFragment;
	private final byte[] padding;

	public HeadersFrame(int streamId, boolean endStream, boolean endHeaders, boolean padded, int padLength,
			final PriorityFrame priorityFrame, final byte[] headerBlockFragment) {

		this.endStream = endStream;
		this.endHeaders = endHeaders;
		this.padded = padded;
		this.priority = priorityFrame != null;

		this.padLength = padLength;
		this.priorityFrame = priorityFrame;
		this.headerBlockFragment = headerBlockFragment;
		if (!padded) {
			this.padding = new byte[0];
		} else {
			this.padding = createBinaryData(padLength, new byte[] { (byte) 0 });
		}

		byte flags = (byte) (this.endStream ? 0x1 : 0x0);
		flags |= (this.endHeaders ? 0x4 : 0x0);
		flags |= (this.padded ? 0x8 : 0x0);
		flags |= (this.priority ? 0x20 : 0x0);
		flags &= 0xff;

		byte[] payload = null;
		if (this.padded) {
			if (this.priority) {
				payload = combine(new byte[] { (byte) this.padLength }, this.priorityFrame.getPayload(),
						this.headerBlockFragment, this.padding);
			} else {
				payload = combine(new byte[] { (byte) this.padLength }, this.headerBlockFragment, this.padding);
			}
		} else {
			if (this.priority) {
				payload = combine(this.priorityFrame.getPayload(), this.headerBlockFragment);
			} else {
				payload = this.headerBlockFragment;
			}
		}

		init(payload.length, HEADERS, flags, streamId, payload);

	}

	public HeadersFrame(int streamId, boolean endStream, boolean endHeaders, byte[] padding,
			final PriorityFrame priorityFrame, final byte[] headerBlockFragment) {
		this(streamId, endStream, endHeaders, padding != null, (padding != null ? padding.length : -1), priorityFrame,
				headerBlockFragment);
	}

	public HeadersFrame(int streamId, boolean endStream, boolean endHeaders, final PriorityFrame priorityFrame,
			final byte[] headerBlockFragment) {
		this(streamId, endStream, endHeaders, false, -1, priorityFrame, headerBlockFragment);
	}

	public HeadersFrame(int streamId, boolean endStream, boolean endHeaders, final byte[] headerBlockFragment) {
		this(streamId, endStream, endHeaders, null, headerBlockFragment);
	}

	public HeadersFrame(final Frame rawFrame) {

		copyFrom(rawFrame);

		this.endStream = (getFlags() & 0x1) == 0x1;
		this.endHeaders = (getFlags() & 0x4) == 0x4;
		this.padded = (getFlags() & 0x8) == 0x8;
		this.priority = (getFlags() & 0x20) == 0x20;

		ByteBuffer payloadBuff = ByteBuffer.wrap(getPayload());

		if (this.padded) {
			this.padLength = payloadBuff.get();
		} else {
			this.padLength = -1;
		}

		if (this.priority) {
			byte[] priorityPayload = new byte[5];
			payloadBuff.get(priorityPayload);
			this.priorityFrame = new PriorityFrame(getStreamId(), priorityPayload);
		} else {
			this.priorityFrame = null;
		}

		this.headerBlockFragment = new byte[getPayloadLength() - this.padLength - 1 - (this.priority ? 5 : 0)];
		payloadBuff.get(this.headerBlockFragment);

		if (this.padLength > 0) {
			this.padding = new byte[this.padLength];
			payloadBuff.get(this.padding);
		} else {
			this.padding = new byte[0];
		}

	}

	@Override
	public String describeFlags() {
		StringBuilder describe = new StringBuilder();
		describe.append("END_STREAM=" + isEndStream()).append(keyValSep);
		describe.append("END_HEADERS=" + isEndHeaders()).append(keyValSep);
		describe.append("PADDED=" + isPadded()).append(keyValSep);
		describe.append("PRIORITY=" + isPriority());
		return describe.toString();
	}

	@Override
	public String describePayload() {

		if (padded) {
			StringBuilder describe = new StringBuilder();
			describe.append("Pad_Length=" + getPadLength()).append(keyValSep);
			if (priorityFrame != null) {
				describe.append(priorityFrame.describePayload()).append(keyValSep);
			}
			describe.append("Header-Block-Fragment=" + showPartOfTextIfTooLong(toHexString(getHeaderBlockFragment())));
			describe.append(keyValSep);
			describe.append("Padding=" + showPartOfTextIfTooLong(toHexString(getPadding())));
			return describe.toString();
		} else {
			if (priorityFrame != null) {
				StringBuilder describe = new StringBuilder();
				describe.append(priorityFrame.describePayload()).append(keyValSep);
				describe.append("Header-Block-Fragment=" + showPartOfTextIfTooLong(toHexString(getHeaderBlockFragment())));
				return describe.toString();
			} else {
				return "Header-Block-Fragment=" + showPartOfTextIfTooLong(toHexString(getHeaderBlockFragment()));
			}
		}

	}

	public boolean isEndStream() {
		return endStream;
	}

	@Override
	public boolean isEndHeaders() {
		return endHeaders;
	}

	@Override
	public boolean isPadded() {
		return padded;
	}

	public boolean isPriority() {
		return priority;
	}

	@Override
	public int getPadLength() {
		return padLength;
	}

	public PriorityFrame getPriorityFrame() {
		return priorityFrame;
	}

	@Override
	public byte[] getHeaderBlockFragment() {
		return headerBlockFragment;
	}

	@Override
	public byte[] getPadding() {
		return padding;
	}

}
