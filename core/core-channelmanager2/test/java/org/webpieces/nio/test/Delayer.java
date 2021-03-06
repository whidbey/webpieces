package org.webpieces.nio.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import org.webpieces.util.logging.Logger;

import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.deprecated.ChannelServiceFactory;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.nio.api.libs.BufferFactory;
import org.webpieces.nio.api.libs.BufferHelper;
import org.webpieces.nio.api.libs.FactoryCreator;


public class Delayer implements DataListener {

	private static final Logger log = LoggerFactory.getLogger(Delayer.class);
	private static final BufferHelper HELPER = ChannelServiceFactory.bufferHelper(null);
	private BufferFactory bufFactory;
	private static Timer timer = new Timer();
	private TCPChannel to;


	public Delayer(TCPChannel to) {
		this.to = to;
		if(bufFactory == null) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put(FactoryCreator.KEY_IS_DIRECT, false);
			FactoryCreator creator = FactoryCreator.createFactory(null);
			bufFactory = creator.createBufferFactory(map);			
		}		
	}
	public void incomingData(Channel channel, ByteBuffer chunk) throws IOException {
		final ByteBuffer newBuffer = bufFactory.createBuffer(channel, chunk.remaining());
		newBuffer.put(chunk);
		TimerTask t = new TimerTask() {
			@Override
			public void run() {
				try {
					HELPER.doneFillingBuffer(newBuffer);
					to.oldWrite(newBuffer);
				} catch (Exception e) {
					log.error("exception", e);
				}
			}
			
		};
		timer.schedule(t, 1000);
	}

	public void farEndClosed(Channel channel) {
		TimerTask t = new TimerTask() {
			@Override
			public void run() {
				try {
					to.oldClose();
				} catch (Exception e) {
					log.error("exception", e);
				}
			}
			
		};
		timer.schedule(t, 1000);
	}
	public void failure(Channel channel, ByteBuffer data, Exception e) {
		log.erroring(channel+"Data not received");
	}

	
}
