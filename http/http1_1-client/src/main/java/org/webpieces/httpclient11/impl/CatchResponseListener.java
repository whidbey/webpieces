package org.webpieces.httpclient11.impl;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.httpclient11.api.DataWriter;
import org.webpieces.httpclient11.api.HttpResponseListener;
import org.webpieces.httpparser.api.dto.HttpData;
import org.webpieces.httpparser.api.dto.HttpResponse;

public class CatchResponseListener implements HttpResponseListener {

	private static final Logger log = LoggerFactory.getLogger(CatchResponseListener.class);
	
	private HttpResponseListener listener;

	public CatchResponseListener(HttpResponseListener listener) {
		this.listener = listener;
	}

	@Override
	public CompletableFuture<DataWriter> incomingResponse(HttpResponse resp, boolean isComplete) {
		try {
			return listener.incomingResponse(resp, isComplete)
					.thenApply(w -> new CatchDataWriter(w));
		} catch(Throwable e) {
			log.error("exception", e);
			CompletableFuture<DataWriter> future = new CompletableFuture<DataWriter>();
			future.completeExceptionally(e);
			return future;
		}
	}

	private class CatchDataWriter implements DataWriter {
		private DataWriter writer;
		public CatchDataWriter(DataWriter writer) {
			this.writer = writer;
		}

		@Override
		public CompletableFuture<Void> incomingData(HttpData chunk) {
			try {
				return writer.incomingData(chunk);
			} catch(Throwable e) {
				log.error("exception", e);
				CompletableFuture<Void> future = new CompletableFuture<Void>();
				future.completeExceptionally(e);
				return future;
			}
		}
	}
	
	@Override
	public void failure(Throwable e) {
		try {
			listener.failure(e);
		} catch(Throwable ee) {
			log.error("exception", ee);
		}
	}

}
