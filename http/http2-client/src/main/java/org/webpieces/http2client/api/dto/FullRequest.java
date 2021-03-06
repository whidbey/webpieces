package org.webpieces.http2client.api.dto;

import org.webpieces.data.api.DataWrapper;

import com.webpieces.hpack.api.dto.Http2Request;
import com.webpieces.hpack.api.dto.Http2Trailers;

public class FullRequest extends Http2Message {

	protected Http2Request headers;

	public FullRequest() {
	}
	
	public FullRequest(Http2Request request, DataWrapper fullData, Http2Trailers trailingHeaders) {
		super(fullData, trailingHeaders);
		this.headers = request;
	}
	
	public Http2Request getHeaders() {
		return headers;
	}

	public void setHeaders(Http2Request headers) {
		this.headers = headers;
	}
}
