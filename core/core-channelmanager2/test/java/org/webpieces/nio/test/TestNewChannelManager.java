package org.webpieces.nio.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.webpieces.util.logging.Logger;

import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.ChannelManagerFactory;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.channels.TCPServerChannel;
import org.webpieces.nio.api.deprecated.ChannelServiceFactory;
import org.webpieces.nio.api.handlers.ConnectionListener;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.nio.api.handlers.FutureOperation;
import org.webpieces.nio.api.handlers.OperationCallback;
import org.webpieces.nio.api.libs.BufferHelper;
import org.webpieces.nio.api.testutil.HandlerForTests;
import org.webpieces.nio.api.testutil.MockDataHandler;
import org.webpieces.nio.api.testutil.MockNIOServer;

import biz.xsoftware.mock.CalledMethod;
import biz.xsoftware.mock.MockObject;
import biz.xsoftware.mock.MockObjectFactory;
import junit.framework.TestCase;

public class TestNewChannelManager extends TestCase {

	private static final Logger log = LoggerFactory.getLogger(TestNewChannelManager.class);
	
	private ChannelManager server;
	private ChannelManager client;
	private TCPChannel client1;
	private InetAddress loopBack;
	private InetSocketAddress loopBackAnyPort;
	private MockObject clientHandler;
	private MockObject clientConnect;
	private TCPServerChannel srvrChannel;
	private MockObject serverAccept;
	private BufferHelper helper = ChannelServiceFactory.bufferHelper(null);

	private Channel serverTcpChannel;

	private MockObject serverHandler;
	
	protected void setUp() throws Exception {
		HandlerForTests.setupLogging();
	
		server = ChannelManagerFactory.createChannelManager("server", null);
		client = ChannelManagerFactory.createChannelManager("client", null);
		
		loopBack = InetAddress.getByName("127.0.0.1");	
		loopBackAnyPort = new InetSocketAddress(loopBack, 0);
		
		serverAccept = MockObjectFactory.createMock(ConnectionListener.class);
		srvrChannel = server.createTCPServerChannel("jmxServer");
		srvrChannel.setReuseAddress(true);
		srvrChannel.bind(loopBackAnyPort);	
		srvrChannel.registerServerSocketChannel((ConnectionListener) serverAccept);
		
		clientHandler = MockObjectFactory.createMock(DataListener.class);
		clientConnect = MockObjectFactory.createMock(OperationCallback.class);
		client1 = client.createTCPChannel("ClientChannel");	
		
	}
	
	protected void tearDown() throws Exception {
		HandlerForTests.checkForWarnings();
	}

	public void testBasic() throws Exception {
		client1.bind(loopBackAnyPort);		
		InetSocketAddress remoteAddr = new InetSocketAddress(loopBack, srvrChannel.getLocalAddress().getPort());
		FutureOperation future = client1.connect(remoteAddr);
		future.setListener((OperationCallback) clientConnect);
		clientConnect.expect("finished");
		
		client1.registerForReads((DataListener)clientHandler);
		
		future.waitForOperation(); //should return immediately since listener fired
		
		serverHandler = MockObjectFactory.createMock(DataListener.class);
		CalledMethod m = serverAccept.expect("connected");
		serverTcpChannel = (Channel)m.getAllParams()[0];
		serverTcpChannel.registerForReads((DataListener) serverHandler);
		
		boolean isConnected = client1.isConnected();
		assertTrue("Client should be connected", isConnected);
		
		verifyDataPassing();
		verifyTearDown();	
	}
	
	private ByteBuffer verifyDataPassing() throws Exception {
		ByteBuffer b = ByteBuffer.allocate(10);
		helper.putString(b, "de");
		helper.doneFillingBuffer(b);
		log.trace("***********************************************");
		FutureOperation write = client1.write(b);
		write.waitForOperation(5000);
		
		CalledMethod m = serverHandler.expect("incomingData");
		TCPChannel actualChannel = (TCPChannel)m.getAllParams()[0];
		ByteBuffer actualBuf = (ByteBuffer)m.getAllParams()[1];
		String result = helper.readString(actualBuf, actualBuf.remaining());
		assertEquals("de", result);
		
		b.rewind();
		FutureOperation future = actualChannel.write(b);
		future.waitForOperation(5000); //synchronously wait for write to happen
		
		m = clientHandler.expect(MockDataHandler.INCOMING_DATA);
		actualBuf = (ByteBuffer) m.getAllParams()[1];
		result = helper.readString(actualBuf, actualBuf.remaining());
		assertEquals("de", result);	

		b.rewind();
		FutureOperation future2 = actualChannel.write(b);
		future2.waitForOperation(5000); //synchronously wait for write to happen

		m = clientHandler.expect(MockDataHandler.INCOMING_DATA);
		actualBuf = (ByteBuffer) m.getAllParams()[1];
		result = helper.readString(actualBuf, actualBuf.remaining());
		assertEquals("de", result);	
		
		return b;
	}
	
	private void verifyTearDown() throws IOException {
        log.info("local="+client1.getLocalAddress()+" remote="+client1.getRemoteAddress());
		log.info("CLIENT1 CLOSE");
		FutureOperation future = client1.close();
		serverHandler.expect(MockNIOServer.FAR_END_CLOSED);
		future.setListener((OperationCallback) clientConnect);
		clientConnect.expect("finished");
	}
}
