package org.webpieces.httpclientx.impl;

import java.util.concurrent.CompletableFuture;

import org.webpieces.http2translations.api.Http1_1ToHttp2;
import org.webpieces.httpclient11.api.DataWriter;
import org.webpieces.httpclient11.api.HttpResponseListener;
import org.webpieces.httpparser.api.dto.HttpData;
import org.webpieces.httpparser.api.dto.HttpResponse;

import com.webpieces.hpack.api.dto.Http2Response;
import com.webpieces.http2engine.api.ResponseHandler;
import com.webpieces.http2engine.api.StreamWriter;
import com.webpieces.http2engine.api.error.ConnectionCancelled;
import com.webpieces.http2engine.api.error.ConnectionFailure;
import com.webpieces.http2engine.api.error.ShutdownStream;
import com.webpieces.http2parser.api.dto.DataFrame;
import com.webpieces.http2parser.api.dto.error.CancelReasonCode;
import com.webpieces.http2parser.api.dto.error.ConnectionException;

public class ResponseListener implements HttpResponseListener {

	private ResponseHandler responseListener;
	private String logId;

	public ResponseListener(String logId, ResponseHandler responseListener) {
		this.logId = logId;
		this.responseListener = responseListener;
	}

	@Override
	public CompletableFuture<DataWriter> incomingResponse(HttpResponse resp, boolean isComplete) {
		Http2Response r = Http1_1ToHttp2.responseToHeaders(resp);
		return responseListener.process(r).thenApply(w -> new DataWriterImpl(w));
	}

	private class DataWriterImpl implements DataWriter {
		private StreamWriter writer;
		public DataWriterImpl(StreamWriter writer) {
			this.writer = writer;
		}

		@Override
		public CompletableFuture<Void> incomingData(HttpData chunk) {
			DataFrame data = Http1_1ToHttp2.translateData(chunk);
			return writer.processPiece(data);
		}
	}
	@Override
	public void failure(Throwable e) {
		ConnectionCancelled connCancelled = new ConnectionFailure(new ConnectionException(CancelReasonCode.BUG, logId, 0, "Failure from connection", e));
		responseListener.cancel(new ShutdownStream(0, connCancelled));
	}

}
