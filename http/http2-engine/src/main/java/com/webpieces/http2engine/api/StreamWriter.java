package com.webpieces.http2engine.api;

import java.util.concurrent.CompletableFuture;

import com.webpieces.http2parser.api.dto.lib.PartialStream;

public interface StreamWriter {

	CompletableFuture<StreamWriter> send(PartialStream data);

}
