package org.webpieces.webserver.impl;

import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.data.api.BufferPool;

import com.webpieces.hpack.api.dto.Http2Request;

class RequestInfo {

	private RouterRequest routerRequest;
	private Http2Request request;
	private BufferPool pool;
	private ResponseOverrideSender responseSender;

	RequestInfo(RouterRequest routerRequest, Http2Request request, BufferPool pool, ResponseOverrideSender responseSender) {
		this.routerRequest = routerRequest;
		this.request = request;
		this.pool = pool;
		this.responseSender = responseSender;
	}

	RouterRequest getRouterRequest() {
		return routerRequest;
	}

	public Http2Request getRequest() {
		return request;
	}

	BufferPool getPool() {
		return pool;
	}

	ResponseOverrideSender getResponseSender() {
		return responseSender;
	}

}
