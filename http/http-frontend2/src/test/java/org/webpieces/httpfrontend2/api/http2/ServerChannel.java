package org.webpieces.httpfrontend2.api.http2;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.webpieces.nio.api.channels.ChannelSession;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.nio.impl.util.ChannelSessionImpl;

public class ServerChannel implements TCPChannel {

	private DataListener listener;
	private TCPChannel clientChannel;
	private ChannelSession channelSession = new ChannelSessionImpl();

	public ServerChannel(TCPChannel clientChannel, DataListener listener) {
		this.clientChannel = clientChannel;
		this.listener = listener;
	}

	@Override
	public CompletableFuture<Void> connect(SocketAddress addr, DataListener listener) {
		throw new IllegalStateException("should be already connected");
	}

	@Override
	public CompletableFuture<Void> write(ByteBuffer b) {
		listener.incomingData(clientChannel, b);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<Void> close() {
		listener.farEndClosed(clientChannel);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return null;
	}

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ChannelSession getSession() {
		return channelSession ;
	}

	@Override
	public boolean isSslChannel() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setReuseAddress(boolean b) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setName(String string) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getChannelId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<Void> bind(SocketAddress addr) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public boolean isBlocking() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isClosed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isBound() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getKeepAlive() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setKeepAlive(boolean b) {
		// TODO Auto-generated method stub
		
	}

}
