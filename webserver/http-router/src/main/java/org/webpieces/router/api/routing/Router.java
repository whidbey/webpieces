package org.webpieces.router.api.routing;

import org.webpieces.ctx.api.HttpMethod;

/**
 * You can override this implementation if you like
 * 
 * @author dhiller
 *
 */
public interface Router {

	void addRoute(HttpMethod method, String path, String controllerMethod, RouteId routeId);
	/**
	 * Use this route to add POST routes with no token check(for apis generally or to turn of the security IF you really
	 * want to).  Also, use this route to add form that use the GET method so the token is checked as well.
	 */
	void addRoute(HttpMethod method, String path, String controllerMethod, RouteId routeId, boolean checkToken);

	void addContentRoute(HttpMethod method, String urlPath, String controllerMethod);
	
	/**
	 * For adding a single url path with different content types backing it.  Clients are forced to set 
	 * Accept header in this case which can be inconvient in some cases but if you like it that way.
	 * (I prefer to give my api different url paths than my web gui)
	 */
	MultiRoute addMultiRoute(HttpMethod method, String path);

	/**
	 * If on the classpath, we use classloader and InputStream.  If not, we use memory mapped files in
	 * hopes that it performs better AND asyncrhonously read such that thread goes and does other 
	 * work until the completionListener callback using AsynchronousFileChannel
	 */
	void addStaticDir(String urlPath, String fileSystemPath, boolean isOnClassPath);

	/**
	 * If on the classpath, we use classloader and InputStream.  If not, we use memory mapped files in
	 * hopes that it performs better AND asyncrhonously read such that thread goes and does other 
	 * work until the completionListener callback using AsynchronousFileChannel
	 */
	void addStaticFile(String urlPath, String fileSystemPath, boolean isOnClassPath);

	<T> void addFilter(String path, Class<? extends RouteFilter<T>> filter, T initialConfig, PortType type);

	<T> void addNotFoundFilter(Class<? extends RouteFilter<T>> filter, T initialConfig, PortType type);

	<T> void addInternalErrorFilter(Class<? extends RouteFilter<T>> filter, T initialConfig, PortType type);

	/**
	 * This is the controller for 404's where the path was not found AND this MUST be set
	 */
	void setPageNotFoundRoute(String controllerMethod);
	
	void setInternalErrorRoute(String controllerMethod);
	
	/**
	 * If you scope your router to /backend, every Router.addRoute path uses that prefix as the final
	 * path
	 * 
	 * @param path
	 * @param isHttpsOnlyRoutes true if only available over https otherwise available over http and https
	 * @return
	 */
	Router getScopedRouter(String path, boolean isHttpsOnlyRoutes);

	/**
	 * Only used if you host multiple domains(like me)!!!!!  All paths refer to all domains EXCEPT the ones defined
	 * in a DomainScopedRouter.  Only domains matching the pattern of domainRegEx will see these pages and
	 * the rest are served a not found (or are served the page defined in another module rather than the
	 * one for the specific domains)
	 *  
	 * @param path
	 * @param isSecure true if only available over https otherwise available over http and https
	 * @return
	 */
	Router getDomainScopedRouter(String domainRegEx);

	void addCrud(String entity, String controller, CrudRouteIds routeIds);

}
