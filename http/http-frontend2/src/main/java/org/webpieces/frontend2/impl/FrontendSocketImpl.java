package org.webpieces.frontend2.impl;

import java.net.InetSocketAddress;

import org.webpieces.frontend2.api.FrontendSocket;
import org.webpieces.frontend2.api.ServerSocketInfo;
import org.webpieces.frontend2.api.StreamListener;
import org.webpieces.httpparser.api.MarshalState;
import org.webpieces.httpparser.api.Memento;
import org.webpieces.nio.api.channels.ChannelSession;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.util.locking.PermitQueue;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.http2engine.api.error.FarEndClosedConnection;
import com.webpieces.http2engine.api.error.ShutdownStream;
import com.webpieces.http2engine.api.server.Http2ServerEngine;

public class FrontendSocketImpl implements FrontendSocket {

	private static final Logger log = LoggerFactory.getLogger(FrontendSocketImpl.class);
	
	private TCPChannel channel;
	private ProtocolType protocol;
	private Memento http1_1ParseState;
	private Http2ServerEngine http2Engine;
	private boolean isClosed;
	private ServerSocketInfo svrSocketInfo;

	private MarshalState http1_1MarshalState;

	private PermitQueue permitQueue = new PermitQueue(1);

	private Http1_1StreamImpl currentStream;

	public FrontendSocketImpl(TCPChannel channel, ProtocolType protocol, ServerSocketInfo svrSocketInfo) {
		this.channel = channel;
		this.protocol = protocol;
		this.svrSocketInfo = svrSocketInfo;
	}

	public ProtocolType getProtocol() {
		return protocol;
	}

	@Override
	public void close(String reason) {
		//need to do goAway here
		internalClose();
	}

	public void internalClose() {
		isClosed = true;
		channel.close();
	}
	
	public void setHttp1_1ParseState(Memento parseState, MarshalState marshalState) {
		this.http1_1ParseState = parseState;
		this.http1_1MarshalState = marshalState;
	}

	public Memento getHttp1_1ParseState() {
		return http1_1ParseState;
	}
	public MarshalState getHttp1_1MarshalState() {
		return http1_1MarshalState;
	}

	public void setProtocol(ProtocolType protocol) {
		this.protocol = protocol;
	}

	public void setHttp2Engine(Http2ServerEngine engine) {
		this.http2Engine = engine;
	}

	public Http2ServerEngine getHttp2Engine() {
		return http2Engine;
	}

	public TCPChannel getChannel() {
		return channel;
	}

	public void farEndClosed(StreamListener httpListener) {
		isClosed = true;
		FarEndClosedConnection conn = new FarEndClosedConnection(this+" The far end closed the socket");
		if(protocol == ProtocolType.HTTP1_1) {
			cancelAllStreams(httpListener, conn);
		}
		
	}

	private void cancelAllStreams(StreamListener httpListener, FarEndClosedConnection f) {
		Http1_1StreamImpl stream = getCurrentStream();
		if(stream == null)
			return;
		
		ShutdownStream shutdown = new ShutdownStream(stream.getStreamId(), f);
		stream.getStreamHandle().incomingCancel(shutdown);
	}

	@Override
	public ChannelSession getSession() {
		return channel.getSession();
	}

	@Override
	public String toString() {
		return "HttpSocket[" + channel+"]";
	}

	@Override
	public boolean isHttps() {
		return svrSocketInfo.isHttps();
	}

	@Override
	public InetSocketAddress getServerLocalBoundAddress() {
		return svrSocketInfo.getLocalBoundAddress();
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return channel.getLocalAddress();
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return channel.getRemoteAddress();
	}

	public PermitQueue getPermitQueue() {
		return permitQueue;
	}

	public void setCurrentStream(Http1_1StreamImpl currentStream) {
		this.currentStream = currentStream;
	}

	public Http1_1StreamImpl getCurrentStream() {
		return currentStream;
	}

}
