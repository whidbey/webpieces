package org.webpieces.webserver.i18n.app;

import static org.webpieces.ctx.api.HttpMethod.GET;

import org.webpieces.router.api.routing.AbstractRoutes;

public class I18nRoutes extends AbstractRoutes {

	@Override
	public void configure() {
		addRoute(GET , "/i18nBasic",         "I18nController.i18nBasic", I18nRouteId.I18N_BASIC);
		
		setPageNotFoundRoute("/org/webpieces/webserver/basic/app/biz/BasicController.notFound");
		setInternalErrorRoute("/org/webpieces/webserver/basic/app/biz/BasicController.internalError");
	}

}
