package org.webpieces.webserver.test;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;

import org.webpieces.httpclient11.api.HttpClient;
import org.webpieces.httpclient11.api.HttpClientFactory;
import org.webpieces.httpclient11.api.HttpSocket;
import org.webpieces.mock.time.MockTime;
import org.webpieces.mock.time.MockTimer;
import org.webpieces.nio.api.BackpressureConfig;
import org.webpieces.webserver.test.http11.DirectHttp11Client;

import com.google.inject.Module;

public class AbstractWebpiecesTest {

	protected MockChannelManager mgr = new MockChannelManager();
	protected MockTime time = new MockTime(true);
	protected MockTimer mockTimer = new MockTimer();

	public HttpSocket connectHttp(boolean isRemote, InetSocketAddress addr) throws InterruptedException, ExecutionException, TimeoutException {
		HttpSocket socket = getClient(isRemote).createHttpSocket();
		CompletableFuture<Void> connect = socket.connect(addr);
		connect.get(2, TimeUnit.SECONDS);
		return socket;
	}

	public HttpSocket connectHttps(boolean isRemote, SSLEngine engine, InetSocketAddress addr) throws InterruptedException, ExecutionException, TimeoutException {
		HttpSocket socket = getClient(isRemote).createHttpsSocket(engine);
		CompletableFuture<Void> connect = socket.connect(addr);
		connect.get(2, TimeUnit.SECONDS);
		return socket;
	}

	protected Module getOverrides(boolean isFullServer) {
		if(isFullServer)
			return new OverridesForTestRealServer();
		return new OverridesForTest(mgr, time, mockTimer);
	}

	protected HttpClient getClient(boolean isRemote) {
		if(isRemote) {
			HttpClient client = HttpClientFactory.createHttpClient("testClient", 5, new BackpressureConfig());
			return client;
		}
		
		/*
		 * The Client that wires itself on top of the server directly such that a developer can step through the
		 * whole webpieces server into their application to get a full picture of what is going on.  (we try to
		 * keep as few layers as possible between client and webapplication code
		 */
		return new DirectHttp11Client(mgr);
	}

}
