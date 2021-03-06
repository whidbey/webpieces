package org.webpieces.frontend2.impl;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.webpieces.frontend2.api.FrontendSocket;
import org.webpieces.frontend2.api.HttpStream;
import org.webpieces.frontend2.api.ResponseStream;
import org.webpieces.frontend2.api.StreamSession;
import org.webpieces.http2translations.api.Http2ToHttp1_1;
import org.webpieces.httpparser.api.HttpParser;
import org.webpieces.httpparser.api.common.Header;
import org.webpieces.httpparser.api.common.KnownHeaderName;
import org.webpieces.httpparser.api.dto.HttpChunk;
import org.webpieces.httpparser.api.dto.HttpData;
import org.webpieces.httpparser.api.dto.HttpLastChunk;
import org.webpieces.httpparser.api.dto.HttpPayload;
import org.webpieces.httpparser.api.dto.HttpResponse;
import org.webpieces.util.locking.PermitQueue;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.hpack.api.dto.Http2Response;
import com.webpieces.http2engine.api.PushStreamHandle;
import com.webpieces.http2engine.api.StreamWriter;
import com.webpieces.http2parser.api.dto.DataFrame;
import com.webpieces.http2parser.api.dto.lib.Http2Header;
import com.webpieces.http2parser.api.dto.lib.Http2HeaderName;
import com.webpieces.http2parser.api.dto.lib.Http2Msg;
import com.webpieces.http2parser.api.dto.lib.StreamMsg;

public class Http1_1StreamImpl implements ResponseStream {
	private static final Logger log = LoggerFactory.getLogger(Http1_1StreamImpl.class);
	//private static final DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();

	private FrontendSocketImpl socket;
	private HttpParser http11Parser;
	private AtomicReference<Http2Msg> endingFrame = new AtomicReference<>();
	private StreamSession session = new StreamSessionImpl();

	private HttpStream streamHandle;

	private int streamId;

	private StreamWriter requestWriter;

	private PermitQueue permitQueue;

	private boolean sentFullRequest;

	public Http1_1StreamImpl(int streamId, FrontendSocketImpl socket, HttpParser http11Parser, PermitQueue permitQueue) {
		this.streamId = streamId;
		this.socket = socket;
		this.http11Parser = http11Parser;
		this.permitQueue = permitQueue;
	}

	@Override
	public CompletableFuture<StreamWriter> sendResponse(Http2Response headers) {
		closeCheck();
		HttpResponse response = Http2ToHttp1_1.translateResponse(headers);
		
		if(headers.isEndOfStream()) {
			validateHeader(response);
			remove(headers);
			return write(response).thenApply(w -> {
				permitQueue.releasePermit();
				return new NoWritesWriter();
			});
		} else if(contentLengthGreaterThanZero(headers)) {
			return write(response).thenApply(w -> new ContentLengthResponseWriter(headers));
		}
		
		return write(response).thenApply(c -> new Http11ChunkedWriter());
	}

	private void closeCheck() {
		if(endingFrame.get() != null)
			throw new IllegalArgumentException("You already sent a frame with endOfStream=true so cannot send more data");
	}

	private void validateHeader(HttpResponse response) {
		Header contentLenHeader = response.getHeaderLookupStruct().getHeader(KnownHeaderName.CONTENT_LENGTH);
		if(contentLenHeader == null)
			throw new IllegalArgumentException("Content Length header required and missing and should be set to zero");
		else if(contentLenHeader.getValue() == null)
			throw new IllegalArgumentException("Content Length header found but it's value is null");
		
		int len = Integer.parseInt(contentLenHeader.getValue());
		if(len != 0)
			throw new IllegalArgumentException("Content Length header found but it's value is 0 while response.isEndOfStream is true.  this is contradictory");
	}

	private boolean contentLengthGreaterThanZero(Http2Response headers) {
		Http2Header contentLenHeader = headers.getHeaderLookupStruct().getHeader(Http2HeaderName.CONTENT_LENGTH);
		if(contentLenHeader != null) {
			int len = Integer.parseInt(contentLenHeader.getValue());
			if(len > 0) //for redirect firefox content len is 0
				return true;
			else if(len == 0) {
				if(!headers.isEndOfStream())
					throw new IllegalStateException("Content-Length=0 but response.isEndOfStream==false");
			}
		}
		return false;
	}

	private class NoWritesWriter implements StreamWriter {
		@Override
		public CompletableFuture<Void> processPiece(StreamMsg data) {
			CompletableFuture<Void> future = new CompletableFuture<>();
			future.completeExceptionally(new IllegalStateException("You already sent a response with endStream==true"));
			return future;
		}
	}
	
	private class ContentLengthResponseWriter implements StreamWriter {
		private int len;
		private int totalWritten;
		
		public ContentLengthResponseWriter(Http2Response response) {
			Http2Header contentLenHeader = response.getHeaderLookupStruct().getHeader(Http2HeaderName.CONTENT_LENGTH);
			this.len = Integer.parseInt(contentLenHeader.getValue());
		}
		
		@Override
		public CompletableFuture<Void> processPiece(StreamMsg data) {
			closeCheck();
			if(!(data instanceof DataFrame))
				throw new UnsupportedOperationException("not supported in http1.1="+data);
			
			DataFrame frame = (DataFrame) data;
			
			totalWritten += frame.getData().getReadableSize();
			if(totalWritten > len)
				throw new IllegalArgumentException("You wrote more than the content length header="+len+" written size="+totalWritten);
			else if(frame.isEndOfStream() && totalWritten != len)
				throw new IllegalArgumentException("You did not write enough data.  written="+totalWritten+" content length header="+len);
			
			if(frame.isEndOfStream()) {
				log.info(socket+" done sending response2");
				remove(data);
			}

			HttpData httpData = new HttpData(frame.getData(), frame.isEndOfStream());
			return write(httpData).thenApply(c -> {
				if(frame.isEndOfStream())
					permitQueue.releasePermit();
				return null;
			});
		}
	}
	
	private class Http11ChunkedWriter implements StreamWriter {

		@Override
		public CompletableFuture<Void> processPiece(StreamMsg data) {
			closeCheck();
			if(!(data instanceof DataFrame))
				throw new UnsupportedOperationException("not supported in http1.1="+data);
			
			DataFrame frame = (DataFrame) data;
			
			CompletableFuture<Void> future = write(new HttpChunk(frame.getData()));
			
			if(data.isEndOfStream()) {
				remove(data);	

				log.info(socket+" done sending response");
				future = future.thenCompose(w -> {
					return write(new HttpLastChunk());
				}).thenApply(v -> {
					permitQueue.releasePermit();
					return null;
				});
			}
			
			return future;
		}
	}

	private void remove(Http2Msg data) {
		Http1_1StreamImpl current = socket.getCurrentStream();
		if(!sentFullRequest)
			throw new IllegalStateException("Client Application cannot send endof stream message until the full request is sent(only in http1.1)");
		else if(endingFrame.get() != null)
			throw new IllegalStateException("You had already sent a frame with endOfStream "
					+ "set and can't send more.  ending frame was="+endingFrame+" but you just sent="+data);
		else if(current != this)
			throw new IllegalStateException("Due to http1.1 spec, YOU MUST return "
					+ "responses in order and this is not the current response that needs responding to");

		endingFrame.set(data);
		socket.setCurrentStream(null);
		
	}
	
	private CompletableFuture<Void> write(HttpPayload payload) {
		ByteBuffer buf = http11Parser.marshalToByteBuffer(socket.getHttp1_1MarshalState(), payload);
		return socket.getChannel().write(buf);
	}
	
	@Override
	public PushStreamHandle openPushStream() {
		throw new UnsupportedOperationException("not supported for http1.1 requests");
	}

	@Override
	public CompletableFuture<Void> cancelStream() {
		throw new UnsupportedOperationException("not supported for http1.1 requests.  you can use getSocket().close() instead if you like");
	}

	@Override
	public FrontendSocket getSocket() {
		return socket;
	}

	@Override
	public StreamSession getSession() {
		return session;
	}

	public void setStreamHandle(HttpStream streamHandle2) {
		this.streamHandle = streamHandle2;
	}

	public HttpStream getStreamHandle() {
		return streamHandle;
	}

	public int getStreamId() {
		return streamId;
	}

	public StreamWriter getRequestWriter() {
		return requestWriter;
	}

	public void setRequestWriter(StreamWriter requestWriter) {
		this.requestWriter = requestWriter;
	}

	public void setSentFullRequest(boolean sent) {
		this.sentFullRequest = sent;
	}

}
