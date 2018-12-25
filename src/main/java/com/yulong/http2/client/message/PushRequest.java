package com.yulong.http2.client.message;

/**
 * An HTTP/2 PUSH request
 */
public class PushRequest {

	private final Http2Headers headers;
	private final int streamId;
	private final int promisedStreamId;

	/**
	 * Constructor for a PUSH request
	 * 
	 * @param headers
	 *            response headers
	 */
	public PushRequest(int streamId, int promisedStreamId, Http2Headers headers) {
		this.streamId = streamId;
		this.promisedStreamId = promisedStreamId;
		this.headers = headers;
	}

	/**
	 * Return the PUSH request method
	 * 
	 * @return reponse status code
	 */
	public String method() {
		return headers.first(PseudoHeader.METHOD.value()).getValue();
	}

	/**
	 * Retrieve the PUSH request headers
	 * 
	 * @return response headers
	 */
	public Http2Headers headers() {
		return headers;
	}

	public int streamId() {
		return this.streamId;
	}

	public int promisedStreamId() {
		return this.promisedStreamId;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("StreamID=");
		sb.append(this.streamId);
		sb.append("\r\n");
		sb.append("PromisedStreamID=");
		sb.append(this.promisedStreamId);
		sb.append("\r\n");
		return sb.toString() + headers.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + promisedStreamId;
		result = prime * result + streamId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PushRequest other = (PushRequest) obj;
		if (promisedStreamId != other.promisedStreamId)
			return false;
		if (streamId != other.streamId)
			return false;
		return true;
	}

}
