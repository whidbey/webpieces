package org.webpieces.httpclient11.impl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.handlers.DataListener;

public class Proxy implements ChannelProxy {

	private TCPChannel channel;

	public Proxy(TCPChannel channel) {
		this.channel = channel;
	}

	@Override
	public CompletableFuture<Void> connect(InetSocketAddress addr, DataListener dataListener) {
		return channel.connect(addr, dataListener);
	}

	@Override
	public CompletableFuture<Void> write(ByteBuffer wrap) {
		return channel.write(wrap);
	}

	@Override
	public CompletableFuture<Void> close() {
		return channel.close();
	}

}
