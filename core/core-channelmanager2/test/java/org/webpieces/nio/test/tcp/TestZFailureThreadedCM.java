package org.webpieces.nio.test.tcp;

import java.util.HashMap;
import java.util.Map;
import org.webpieces.util.logging.Logger;

import org.webpieces.nio.api.deprecated.ChannelManagerOld;
import org.webpieces.nio.api.deprecated.ChannelService;
import org.webpieces.nio.api.deprecated.ChannelServiceFactory;
import org.webpieces.nio.api.deprecated.Settings;
import org.webpieces.nio.api.libs.FactoryCreator;
import org.webpieces.nio.api.libs.PacketProcessorFactory;
import org.webpieces.nio.api.libs.StartableExecutorService;


public class TestZFailureThreadedCM extends ZNioFailureSuperclass {

	private ChannelServiceFactory factory;
	private StartableExecutorService execFactory;
	private PacketProcessorFactory procFactory;
	private Settings factoryHolder;
	
	public TestZFailureThreadedCM(String name) {
		super(name);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put(FactoryCreator.KEY_NUM_THREADS, 10);
		FactoryCreator creator = FactoryCreator.createFactory(null);
		execFactory = creator.createExecSvcFactory(map);
		
		ChannelServiceFactory basic = ChannelServiceFactory.createFactory(null);
		
		Map<String, Object> props = new HashMap<String, Object>();
		props.put(ChannelServiceFactory.KEY_IMPLEMENTATION_CLASS, ChannelServiceFactory.VAL_PACKET_CHANNEL_MGR);
		props.put(ChannelServiceFactory.KEY_CHILD_CHANNELMGR_FACTORY, basic);
		ChannelServiceFactory packetFactory = ChannelServiceFactory.createFactory(props);

		Map<String, Object> props2 = new HashMap<String, Object>();
		props2.put(ChannelServiceFactory.KEY_IMPLEMENTATION_CLASS, ChannelServiceFactory.VAL_THREAD_CHANNEL_MGR);
		props2.put(ChannelServiceFactory.KEY_CHILD_CHANNELMGR_FACTORY, packetFactory);
		factory = ChannelServiceFactory.createFactory(props2);
		procFactory = creator.createPacketProcFactory(null);		
		factoryHolder = new Settings(null, procFactory);		
	}
	
	private static final Logger log = LoggerFactory.getLogger(TestZFailureThreadedCM.class);
	@Override
	protected void setUpImpl() throws Exception {
		super.setUpImpl();
		
		log.info("need some more logging to see what is going on with autobuild.");
		Thread.sleep(1000);
	}


	@Override
	protected ChannelService getClientChanMgr() { 		
		Map<String, Object> p = new HashMap<String, Object>();
		p.put(ChannelManagerOld.KEY_ID, "[client]");
		p.put(ChannelManagerOld.KEY_BUFFER_FACTORY, getBufFactory());
		p.put(ChannelManagerOld.KEY_EXECUTORSVC_FACTORY, execFactory);
		
		return factory.createChannelManager(p);
	}

	@Override
	protected ChannelService getServerChanMgr() {
		Map<String, Object> p = new HashMap<String, Object>();
		p.put(ChannelManagerOld.KEY_ID, "[server]");
		p.put(ChannelManagerOld.KEY_BUFFER_FACTORY, getBufFactory());
		p.put(ChannelManagerOld.KEY_EXECUTORSVC_FACTORY, execFactory);
		return factory.createChannelManager(p);
	}
	@Override
	protected Settings getClientFactoryHolder() {
		return factoryHolder;
	}
	@Override
	protected Settings getServerFactoryHolder() {
		return factoryHolder;
	}
	@Override
	protected String getChannelImplName() {
		return "org.webpieces.nio.impl.cm.threaded.ThdTCPChannel";
	}

	@Override
	protected String getServerChannelImplName() {
		return "org.webpieces.nio.impl.cm.threaded.ThdTCPServerChannel";
	}
	
	public void specialCaseThreadedCM() throws InterruptedException {
		Thread.sleep(3000);
	}

	@Override
	public void testClientThrowsIntoDataHandlerIncomingData() throws Exception {
		super.testClientThrowsIntoDataHandlerIncomingData();
	}
	
	
}
