package org.webpieces.http2client.impl;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.http2engine.api.StreamHandle;
import com.webpieces.http2engine.api.client.Http2ClientEngine;

public class Layer1Incoming implements DataListener {

	private static final Logger log = LoggerFactory.getLogger(Layer1Incoming.class);
	private static final DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();
	private Http2ClientEngine layer2;

	public Layer1Incoming(Http2ClientEngine layer2) {
		this.layer2 = layer2;
	}

	public CompletableFuture<Void> sendInitialFrames() {
		return layer2.sendInitializationToSocket();
	}
	
	public CompletableFuture<Void> sendPing() {
		return layer2.sendPing();
	}
	
	public StreamHandle openStream() {
		return layer2.openStream();
	}

	@Override
	public CompletableFuture<Void> incomingData(Channel channel, ByteBuffer b) {
		log.debug(() -> channel+"incoming data. size="+b.remaining());
		DataWrapper data = dataGen.wrapByteBuffer(b);
		//log.info("data="+data.createStringFrom(0, data.getReadableSize(), StandardCharsets.UTF_8));
		return layer2.parse(data);
	}

	@Override
	public void farEndClosed(Channel channel) {
		layer2.farEndClosed();
	}

	@Override
	public void failure(Channel channel, ByteBuffer data, Exception e) {
		log.warn("failure", e);
	}

}
