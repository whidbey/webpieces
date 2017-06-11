package org.webpieces.nio.api.integ;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.webpieces.data.api.BufferPool;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

final class ClientDataListener implements DataListener {
	private static final Logger log = LoggerFactory.getLogger(ClientDataListener.class);
	
	private BufferPool pool2;
	private BytesRecorder recorder;
	
	public ClientDataListener(BufferPool pool2, BytesRecorder recorder) {
		this.pool2 = pool2;
		this.recorder = recorder;
	}
	
	@Override
	public CompletableFuture<Void> incomingData(Channel channel, ByteBuffer b) {
		recorder.recordBytes(b.remaining());
		
		b.position(b.limit());
		pool2.releaseBuffer(b);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void farEndClosed(Channel channel) {
		log.info("far end closed");
	}

	@Override
	public void failure(Channel channel, ByteBuffer data, Exception e) {
		log.info("failure", e);
	}
	
}