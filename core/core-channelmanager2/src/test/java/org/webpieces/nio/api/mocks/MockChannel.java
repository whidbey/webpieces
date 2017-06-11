package org.webpieces.nio.api.mocks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import org.webpieces.mock.MethodEnum;
import org.webpieces.mock.MockSuperclass;
import org.webpieces.nio.api.MockSelectionKey;
import org.webpieces.nio.api.jdk.JdkSocketChannel;

public class MockChannel extends MockSuperclass implements JdkSocketChannel {

	enum Method implements MethodEnum {
		CONNECT,
		FAR_END_CLOSED,
		FAILURE,
		FINISH_CONNECT
	}

	private MockSelectionKey selectionKey;

	public void addConnectReturnValue(boolean isConnected) {
		super.addValueToReturn(Method.CONNECT, isConnected);
	}
	@Override
	public boolean connect(SocketAddress addr) throws IOException {
		return (boolean) super.calledMethod(Method.CONNECT, addr);
	}
	
	@Override
	public void configureBlocking(boolean b) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isBlocking() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void bind(SocketAddress addr) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isBound() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int write(ByteBuffer b) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int read(ByteBuffer b) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isClosed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setReuseAddress(boolean b) throws SocketException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public InetAddress getInetAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getPort() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public InetAddress getLocalAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getLocalPort() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void finishConnect() throws IOException {
		super.calledVoidMethod(Method.FINISH_CONNECT, true);
	}

	public int getNumTimesFinishConnectCalled() {
		return getCalledMethodList(Method.FINISH_CONNECT).size();
	}
	
	@Override
	public void setKeepAlive(boolean b) throws SocketException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean getKeepAlive() throws SocketException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getSoTimeout() throws SocketException {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public boolean isOpen() {
		return true;
	}
	@Override
	public SelectionKey register(int allOps, Object struct) {
		if(selectionKey == null)
			selectionKey = new MockSelectionKey();
		
		selectionKey.interestOps(allOps);
		selectionKey.attach(struct);
		
		return selectionKey;
	}
	
	@Override
	public SelectionKey keyFor() {
		return selectionKey;
	}

	public void setConnectReady() {
		selectionKey.setConnectReady();
	}
	@Override
	public void resetRegisteredOperations(int ops) {
		selectionKey.interestOps(ops);
		if(ops == 0)
			selectionKey = null;
	}
	public SelectionKey getKey() {
		if(selectionKey == null)
			return null;
		
		int val = selectionKey.interestOps() & selectionKey.readyOps();
		if(val > 0)
			return selectionKey;
		return null;
	}
	public boolean isRegisteredForReads() {
		if(selectionKey == null)
			return false;
		if((selectionKey.interestOps() & SelectionKey.OP_READ) > 0)
			return true;
		
		return false;
	}

}
