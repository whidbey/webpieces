package org.webpieces.webserver.test.http11;

import javax.net.ssl.SSLEngine;

import org.webpieces.data.api.BufferCreationPool;
import org.webpieces.httpclient11.api.HttpClient;
import org.webpieces.httpclient11.api.HttpSocket;
import org.webpieces.httpclient11.impl.HttpSocketImpl;
import org.webpieces.httpparser.api.HttpParser;
import org.webpieces.httpparser.api.HttpParserFactory;
import org.webpieces.nio.api.handlers.ConnectionListener;
import org.webpieces.webserver.test.MockChannelManager;
import org.webpieces.webserver.test.MockTcpChannel;

/**
 * An Http1.1 Client that sits directly on top of the webserver such that you can step into the webserver
 * from the test case to understand the full stack including your application and the platform
 * 
 * @author dhiller
 *
 */
public class DirectHttp11Client implements HttpClient {

	private MockChannelManager mgr;
	private HttpParser parser = HttpParserFactory.createParser(new BufferCreationPool());

	public DirectHttp11Client(MockChannelManager mgr) {
		this.mgr = mgr;
	}

	public HttpSocket createHttpSocket() {
		ConnectionListener listener = mgr.getHttpConnection();
		MockTcpChannel channel = new MockTcpChannel();

		try {
			return new HttpSocketImpl(new DelayedProxy(listener, channel), parser);
			//return new Http11SocketImpl(listener, channel, parser, false);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public HttpSocket createHttpsSocket(SSLEngine engine) {
		ConnectionListener listener = mgr.getHttpsConnection();
		MockTcpChannel channel = new MockTcpChannel();

		try {
			return new HttpSocketImpl(new DelayedProxy(listener, channel), parser);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}	
	}
}
