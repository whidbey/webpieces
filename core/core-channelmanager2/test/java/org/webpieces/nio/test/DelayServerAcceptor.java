package org.webpieces.nio.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import org.webpieces.util.logging.Logger;

import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.channels.RegisterableChannel;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.deprecated.ChannelService;
import org.webpieces.nio.api.handlers.ConnectionListener;


public class DelayServerAcceptor implements ConnectionListener {

	private static final Logger log = LoggerFactory.getLogger(DelayServerAcceptor.class);
	private InetSocketAddress realSvr;
	private ChannelService clientSideChanMgr;
	
	private List<TCPChannel> sockets = new LinkedList<TCPChannel>();
	
	private TCPChannel currentChannel;
	private InetAddress delaySvrAddr;

	
	public DelayServerAcceptor(ChannelService clientSideChanMgr, InetAddress delaySvrAddr, InetSocketAddress realSvr) {
		this.clientSideChanMgr = clientSideChanMgr;
		this.realSvr = realSvr;
		this.delaySvrAddr = delaySvrAddr;
	}
		
	public void connected(Channel channel) throws IOException {
		if(log.isTraceEnabled())
			log.trace(channel+"about to accept");
		currentChannel = clientSideChanMgr.createTCPChannel("xxx", null);
        currentChannel.setName("<not known yet>");
		InetSocketAddress addr = new InetSocketAddress(delaySvrAddr, 0);
		currentChannel.bind(addr);
		currentChannel.oldConnect(realSvr);
		if(log.isTraceEnabled())
			log.trace(channel+"connected to real server");
		
		if(log.isTraceEnabled())
			log.trace(channel+":"+currentChannel+"connected all links");
		sockets.add((TCPChannel) channel);
		sockets.add(currentChannel);

		currentChannel.registerForReads(new Delayer((TCPChannel) channel));
		channel.registerForReads(new Delayer(currentChannel));
	}

	public void failed(RegisterableChannel channel, Throwable e) {
		log.error("exception", e);
	}

	public void closeAllSockets() throws IOException {
		for(int i = 0; i < sockets.size(); i++) {
			Channel channel = sockets.get(i);
			channel.oldClose();
		}		
	}
	
//	private class IdHolder {
//		private Object id = "(Id not assigned yet)";
//		public void setId(Object id) {
//			this.id = id;
//		}
//		public String toString() {
//			return id+"";
//		}
//	}
}
