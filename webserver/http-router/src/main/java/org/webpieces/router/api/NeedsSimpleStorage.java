package org.webpieces.router.api;

import java.util.concurrent.CompletableFuture;

public interface NeedsSimpleStorage {

	public CompletableFuture<Void> init(SimpleStorage storage);
	
}
