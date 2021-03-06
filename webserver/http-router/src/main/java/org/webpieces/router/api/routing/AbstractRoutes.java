package org.webpieces.router.api.routing;

import org.webpieces.ctx.api.HttpMethod;

public abstract class AbstractRoutes implements Routes {

	protected Router router;

	@Override
	public void configure(Router router) {
		this.router = router;
		configure();
	}

	protected abstract void configure();
	
	public void addRoute(HttpMethod method, String path, String controllerMethod, RouteId routeId) {
		router.addRoute(method, path, controllerMethod, routeId);
	}

	public void addRoute(HttpMethod method, String path, String controllerMethod, RouteId routeId, boolean checkToken) {
		router.addRoute(method, path, controllerMethod, routeId, checkToken);
	}

	public void addContentRoute(HttpMethod method, String urlPath, String controllerMethod) {
		router.addContentRoute(method, urlPath, controllerMethod);
	}

	public MultiRoute addMultiRoute(HttpMethod method, String path) {
		return router.addMultiRoute(method, path);
	}

	public void addStaticDir(String urlPath, String fileSystemPath, boolean isOnClassPath) {
		router.addStaticDir(urlPath, fileSystemPath, isOnClassPath);
	}
	
	public void addStaticFile(String urlPath, String fileSystemPath, boolean isOnClassPath) {
		router.addStaticFile(urlPath, fileSystemPath, isOnClassPath);
	}
	
	public <T> void addFilter(String path, Class<? extends RouteFilter<T>> filter, T initialConfig, PortType type) {
		router.addFilter(path, filter, initialConfig, type);
	}

	public <T> void addNotFoundFilter(Class<? extends RouteFilter<T>> filter, T initialConfig, PortType type) {
		router.addNotFoundFilter(filter, initialConfig, type);
	}

	public <T> void addInternalErrorFilter(Class<? extends RouteFilter<T>> filter, T initialConfig, PortType type) {
		router.addNotFoundFilter(filter, initialConfig, type);
	}
	
	public void setPageNotFoundRoute(String controllerMethod) {
		router.setPageNotFoundRoute(controllerMethod);
	}

	public void setInternalErrorRoute(String controllerMethod) {
		router.setInternalErrorRoute(controllerMethod);
	}

	public Router getScopedRouter(String path, boolean isHttpsOnlyRoutes) {
		return router.getScopedRouter(path, isHttpsOnlyRoutes);
	}

	public void addCrud(String entity, String controller, CrudRouteIds routes) {
		router.addCrud(entity, controller, routes);
	}	

}
