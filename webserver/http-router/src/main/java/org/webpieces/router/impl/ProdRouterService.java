package org.webpieces.router.impl;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.webpieces.ctx.api.RequestContext;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.api.ResponseStreamer;
import org.webpieces.router.api.RouterService;
import org.webpieces.router.api.actions.Action;
import org.webpieces.router.api.dto.MethodMeta;
import org.webpieces.router.api.exceptions.NotFoundException;
import org.webpieces.router.impl.hooks.ClassForName;
import org.webpieces.router.impl.loader.HaveRouteException;
import org.webpieces.router.impl.loader.ProdClassForName;
import org.webpieces.router.impl.model.MatchResult;
import org.webpieces.router.impl.params.ObjectTranslator;
import org.webpieces.util.filters.Service;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

@Singleton
public class ProdRouterService extends AbstractRouterService implements RouterService {

	private static final Logger log = LoggerFactory.getLogger(ProdRouterService.class);
	
	private RouteLoader routeLoader;
	private ClassForName loader;
	
	@Inject
	public ProdRouterService(
			RouteLoader routeLoader, 
			CookieTranslator cookieTranslator, 
			ObjectTranslator translator, 
			ProdClassForName loader
	) {
		super(routeLoader, cookieTranslator, translator);
		this.routeLoader = routeLoader;
		this.loader = loader;
	}

	//add Route HOOK callback so translate RouteId -> route and route->controller.method to call
	@Override
	public void start() {
		log.info("Starting PROD server with NO compiling classloader");
		
		routeLoader.load(loader, injector -> runStartupHooks(injector));
		started = true;
	}

	@Override
	public void stop() {
		started = false;
	}

	@Override
	public CompletableFuture<Void> incomingRequestImpl(RequestContext ctx, ResponseStreamer responseCb) {
		MatchResult result = fetchRoute(ctx);
		
		//this only prints if in debug mode
		routeLoader.printAllRoutes(result.getMeta().getRoute() );
	
		CompletableFuture<Void> future;
		try {
			ProdErrorRoutes errorRoutes = new ProdErrorRoutes(ctx.getRequest(), routeLoader);
			future = routeLoader.invokeRoute(result, ctx, responseCb, errorRoutes);
		} catch(Throwable e) {
			future = new CompletableFuture<Void>();
			future.completeExceptionally(e);
		}
		return future.exceptionally( t -> { 
			throw new HaveRouteException(result, t); 
			
		});
	}

	//This only exists so dev mode can swap it out and load error routes dynamically as code changes..
	private static class ProdErrorRoutes implements ErrorRoutes {
		private RouteLoader routeLoader;
		private RouterRequest req;
		public ProdErrorRoutes(RouterRequest req, RouteLoader routeLoader) {
			this.req = req;
			this.routeLoader = routeLoader;
		}

		public NotFoundInfo fetchNotfoundRoute(NotFoundException e) {
			//not found is normal in prod mode so we don't log that and only log warnings in dev mode
			RouteMeta result = routeLoader.fetchNotFoundRoute(req.domain);

			//every request for not found route must apply filters(unlike other routes).  There are tests
			//for this use case with the LoginFitler in TestHttps
			Service<MethodMeta, Action> svc = routeLoader.createNotFoundService(result, req);
			
			return new NotFoundInfo(result, svc, req);
		}
		
		public RouteMeta fetchInternalServerErrorRoute() {
			return routeLoader.fetchInternalErrorRoute(req.domain);
		}
	}

	@Override
	protected ErrorRoutes getErrorRoutes(RequestContext ctx) {
		return new ProdErrorRoutes(ctx.getRequest(), routeLoader);
	}

}
