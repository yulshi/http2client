package com.yulong.http2.client.frame;

import static com.yulong.http2.client.utils.Utils.toHexString;

public class ContinuationFrame extends Frame implements Continuable {

	private final boolean endHeaders;
	private final byte[] headerBlockFragment;

	public ContinuationFrame(int streamId, boolean endHeaders, final byte[] headerBlockFragment) {

		this.endHeaders = endHeaders;
		this.headerBlockFragment = headerBlockFragment;

		byte flags = (byte) (this.endHeaders ? 0x4 : 0x0);

		init(headerBlockFragment.length, FrameType.CONTINUATION, flags, streamId, headerBlockFragment);
	}

	public ContinuationFrame(final Frame rawFrame) {

		copyFrom(rawFrame);

		this.endHeaders = (rawFrame.getFlags() & 0x4) == 0x4;
		this.headerBlockFragment = rawFrame.getPayload();

	}

	@Override
	public boolean isEndHeaders() {
		return endHeaders;
	}

	@Override
	public byte[] getHeaderBlockFragment() {
		return headerBlockFragment;
	}

	@Override
	public String describeFlags() {
		return "END_HEADERS=" + this.endHeaders;
	}

	@Override
	public String describePayload() {
		return "Header-Block-Fragment=" + toHexString(this.headerBlockFragment);
	}

}
