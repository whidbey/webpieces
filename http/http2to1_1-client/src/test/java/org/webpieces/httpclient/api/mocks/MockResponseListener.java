package org.webpieces.httpclient.api.mocks;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.webpieces.mock.MethodEnum;
import org.webpieces.mock.MockSuperclass;
import org.webpieces.mock.ParametersPassedIn;

import com.webpieces.hpack.api.dto.Http2Response;
import com.webpieces.http2engine.api.PushStreamHandle;
import com.webpieces.http2engine.api.ResponseHandler;
import com.webpieces.http2engine.api.StreamWriter;
import com.webpieces.http2parser.api.dto.CancelReason;

public class MockResponseListener extends MockSuperclass implements ResponseHandler {

	private enum Method implements MethodEnum {
		PROCESS
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public CompletableFuture<StreamWriter> process(Http2Response response) {
		return (CompletableFuture<StreamWriter>) super.calledMethod(Method.PROCESS, response);
	}

	@Override
	public PushStreamHandle openPushStream() {
		return null;
	}

	@Override
	public CompletableFuture<Void> cancel(CancelReason payload) {
		return null;
	}

	public Http2Response getIncomingMsg() {
		List<ParametersPassedIn> params = super.getCalledMethodList(Method.PROCESS);
		if(params.size() != 1)
			throw new IllegalArgumentException("was not called exactly once.  times="+params.size());
		return (Http2Response) params.get(0).getArgs()[0];
	}

	public void addProcessResponse(CompletableFuture<StreamWriter> future) {
		super.addValueToReturn(Method.PROCESS, future);
	}

}
