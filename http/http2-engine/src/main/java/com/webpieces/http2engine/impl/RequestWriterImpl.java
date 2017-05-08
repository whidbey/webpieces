package com.webpieces.http2engine.impl;

import java.util.concurrent.CompletableFuture;

import com.webpieces.http2engine.api.client.ClientStreamWriter;
import com.webpieces.http2engine.impl.client.Level3ClientStreams;
import com.webpieces.http2engine.impl.shared.Stream;
import com.webpieces.http2parser.api.dto.lib.PartialStream;

public class RequestWriterImpl implements ClientStreamWriter {

	private Stream stream;
	private Level3ClientStreams clientSm;
	private boolean streamEnded;

	public RequestWriterImpl(Stream stream, Level3ClientStreams clientSm) {
		this.stream = stream;
		this.clientSm = clientSm;
	}

	@Override
	public CompletableFuture<ClientStreamWriter> send(PartialStream data) {
		if(data.getStreamId() != stream.getStreamId())
			throw new IllegalArgumentException("PartialStream has incorrect stream id="+data
					+" it should be="+stream.getStreamId()+" since initial request piece had that id");
		else if(streamEnded)
			throw new IllegalArgumentException("Your client already sent in a PartialStream "
					+ "with endOfStream=true.  you can't send more data. offending data="+data);
		
		if(data.isEndOfStream())
			streamEnded = true;
		
		return clientSm.sendMoreStreamData(stream, data).thenApply(c -> this);
	}

}
