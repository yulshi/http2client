package com.yulong.http2.client.message;

import java.util.HashSet;
import java.util.Set;

public enum PseudoHeader {

	METHOD(":method"), 
	SCHEME(":scheme"), 
	AUTHORITY(":authority"), 
	PATH(":path"), 
	STATUS(":status");

	private final String value;
	private static final Set<String> PSEUDO_HEADERS = new HashSet<String>();

	static {
		for (PseudoHeader pseudoHeader : PseudoHeader.values()) {
			PSEUDO_HEADERS.add(pseudoHeader.value());
		}
	}

	PseudoHeader(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static boolean isPseudoHeader(String header) {
		return PSEUDO_HEADERS.contains(header);
	}

}
