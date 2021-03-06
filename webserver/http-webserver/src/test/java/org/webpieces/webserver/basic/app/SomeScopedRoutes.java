package org.webpieces.webserver.basic.app;

import static org.webpieces.ctx.api.HttpMethod.GET;

import org.webpieces.router.api.routing.ScopedRoutes;

public class SomeScopedRoutes extends ScopedRoutes {

	@Override
	protected String getScope() {
		return "/scoped";
	}

	@Override
	protected boolean isHttpsOnlyRoutes() {
		return false;
	}

	@Override
	protected void configure() {
		//special corner case outside regex that we allow so /scope will match
		addRoute(GET , "",         "biz/BasicController.myMethod", BasicRouteId.SCOPED_ROOT);
		//special case matching /scope/
		addRoute(GET , "/",        "biz/BasicController.myMethod", BasicRouteId.SCOPED_ROOT2);
	}

}
