package org.webpieces.router.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.impl.compression.FileMeta;

public interface RouterService {

	void start();

	void stop();

	CompletableFuture<Void> incomingCompleteRequest(RouterRequest req, ResponseStreamer streamer);

	/**
	 * This is exposed as the webserver wires router and templating engine and the templating engine needs a callback to
	 * reverse all routeIds in the html file to actual urls which only the router has knowledge of.
	 * 
	 * @param routeId
	 * @param notUrlEncodedArgs
	 * @param isValidating 
	 * @return
	 */
	String convertToUrl(String routeId, Map<String, String> notUrlEncodedArgs, boolean isValidating);
	
	FileMeta relativeUrlToHash(String urlPath);

	<T> ObjectStringConverter<T> getConverterFor(T bean);
	
}
