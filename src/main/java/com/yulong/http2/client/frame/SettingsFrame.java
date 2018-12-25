package com.yulong.http2.client.frame;

import static java.util.Base64.getUrlEncoder;
import static com.yulong.http2.client.frame.FrameType.SETTINGS;
import static com.yulong.http2.client.utils.Utils.bytes2String;
import static com.yulong.http2.client.utils.Utils.combine;
import static com.yulong.http2.client.utils.Utils.fromHexString;
import static com.yulong.http2.client.utils.Utils.fromInt;
import static com.yulong.http2.client.utils.Utils.toInt;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

import com.yulong.http2.client.common.Constants;
import com.yulong.http2.client.common.SettingsRegistry;

public final class SettingsFrame extends Frame {

	private final boolean ack;
	private final SortedMap<SettingsRegistry, Integer> settings;

	public static final SettingsFrame EMPTY = new SettingsFrame(Collections.emptySortedMap());
	public static final SettingsFrame REPLY = new SettingsFrame(true, Collections.emptySortedMap());

	public SettingsFrame(final SortedMap<SettingsRegistry, Integer> settingsInput) {
		this(false, settingsInput);
	}

	public SettingsFrame(boolean ack, final SortedMap<SettingsRegistry, Integer> settingsInput) {

		this.ack = ack;
		if (settingsInput == null) {
			this.settings = Collections.unmodifiableSortedMap(new TreeMap<>());
		} else {
			this.settings = Collections.unmodifiableSortedMap(settingsInput);
		}

		byte flags = ack ? (byte) 1 : (byte) 0;

		byte[] payload = new byte[] {};
		if (settings != null && !settings.isEmpty()) {
			int payloadLength = settings.size() * 6;
			ByteBuffer bb = ByteBuffer.allocate(payloadLength);
			for (SortedMap.Entry<SettingsRegistry, Integer> entry : settings.entrySet()) {
				SettingsRegistry key = entry.getKey();
				int val = entry.getValue();
				byte[] oneSetting = combine(fromInt(key.getCode(), 2), fromInt(val, 4));
				bb.put(oneSetting);
				payload = bb.array();
			}
		}

		init(payload.length, SETTINGS, flags, 0, payload);

	}

	public SettingsFrame(Frame rawFrame) {

		copyFrom(rawFrame);

		this.ack = ((getFlags() & 0x1) == 0x1);
		SortedMap<SettingsRegistry, Integer> settings_tmp = new TreeMap<>();

		ByteBuffer bb = ByteBuffer.wrap(getPayload());
		int settingsSize = getPayload().length / 6;
		for (int i = 0; i < settingsSize; i++) {
			SettingsRegistry key = SettingsRegistry.from(toInt(bb, i * 6, 2));
			int val = toInt(bb, i * 6 + 2, 4);
			settings_tmp.put(key, val);
		}
		settings = Collections.unmodifiableSortedMap(settings_tmp);

	}

	@Override
	public String describeFlags() {
		return "ACK=" + ack;
	}

	@Override
	public String describePayload() {
		if (settings.isEmpty()) {
			return "";
		}
		StringBuilder describe = new StringBuilder();
		for (SortedMap.Entry<SettingsRegistry, Integer> entry : settings.entrySet()) {
			SettingsRegistry key = entry.getKey();
			int val = entry.getValue();
			describe.append(key.name()).append("=").append(val).append(";");
		}
		return describe.substring(0, describe.length() - 1);
	}

	public boolean isAck() {
		return ack;
	}

	public SortedMap<SettingsRegistry, Integer> getSettings() {
		return settings;
	}

	public String toUrlEncoded() {
		return bytes2String(getUrlEncoder().encode(getPayload()));
	}

	public byte[] toConnectionPreface() {
		return combine(fromHexString(Constants.PRE_PREFACE_HEX), asBytes());
	}

}
