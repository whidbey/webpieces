package org.webpieces.webserver.dev.app;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.webpieces.router.api.actions.Action;
import org.webpieces.router.api.actions.Actions;

@Singleton
public class DevController {

	@Inject
	private NewInterface library;
	
	public Action home() {
		String user = library.fetchName();
		return Actions.renderThis("user", user);
	}

	public Action existingRoute() {
		return Actions.renderThis();
	}
	
	public Action notFound() {
		return Actions.renderThis("value", "something1");
	}
	
	public Action causeError() {
		throw new RuntimeException("testing");
	}
	
	public Action internalError() {
		return Actions.renderThis("error", "error1");
	}
}
