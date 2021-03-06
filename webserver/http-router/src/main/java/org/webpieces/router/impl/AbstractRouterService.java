package org.webpieces.router.impl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.webpieces.ctx.api.FlashSub;
import org.webpieces.ctx.api.RequestContext;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.ctx.api.Session;
import org.webpieces.ctx.api.Validation;
import org.webpieces.router.api.ObjectStringConverter;
import org.webpieces.router.api.ResponseStreamer;
import org.webpieces.router.api.RouterService;
import org.webpieces.router.api.Startable;
import org.webpieces.router.api.exceptions.BadCookieException;
import org.webpieces.router.impl.compression.FileMeta;
import org.webpieces.router.impl.ctx.FlashImpl;
import org.webpieces.router.impl.ctx.SessionImpl;
import org.webpieces.router.impl.ctx.ValidationImpl;
import org.webpieces.router.impl.loader.HaveRouteException;
import org.webpieces.router.impl.model.MatchResult;
import org.webpieces.router.impl.params.ObjectTranslator;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

public abstract class AbstractRouterService implements RouterService {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractRouterService.class);
	protected boolean started = false;
	private RouteLoader routeLoader;
	private ObjectTranslator translator;
	private CookieTranslator cookieTranslator;
	
	public AbstractRouterService(RouteLoader routeLoader, CookieTranslator cookieTranslator, ObjectTranslator translator) {
		this.routeLoader = routeLoader;
		this.cookieTranslator = cookieTranslator;
		this.translator = translator;
	}

	@Override
	public final CompletableFuture<Void> incomingCompleteRequest(RouterRequest routerRequest, ResponseStreamer responseCb) {
		try {
			if(!started)
				throw new IllegalStateException("Either start was not called by client or start threw an exception that client ignored and must be fixed");;
			
			Session session = (Session) cookieTranslator.translateCookieToScope(routerRequest, new SessionImpl(translator));
			FlashSub flash = (FlashSub) cookieTranslator.translateCookieToScope(routerRequest, new FlashImpl(translator));
			Validation validation = (Validation) cookieTranslator.translateCookieToScope(routerRequest, new ValidationImpl(translator));
			RequestContext requestCtx = new RequestContext(validation, flash, session, routerRequest);
			
			return processRequest(requestCtx, responseCb);
			
		} catch(BadCookieException e) {
			throw e;
		} catch (Throwable e) {
			log.warn("uncaught exception", e);
			return responseCb.failureRenderingInternalServerErrorPage(e);
		}
	}

	private CompletableFuture<Void> processRequest(RequestContext requestCtx, ResponseStreamer responseCb) {
		CompletableFuture<Void> future;
		try {
			future = incomingRequestImpl(requestCtx, responseCb);
		} catch(Throwable e) {
			future = new CompletableFuture<Void>();
			future.completeExceptionally(e);			
		}
		future.exceptionally(e -> processException(responseCb, requestCtx, e));
		return future;
	}

	private Void processException(ResponseStreamer responseCb, RequestContext requestCtx, Throwable e) {
		Object meta = "unknown RouteMeta"; 
		//Damn them for wrapping in CompletionException making life really hard....(they should do it the scala way!!!!)
		//that decision results in the below mess instead of clean code
		if(e instanceof HaveRouteException) {
			HaveRouteException exc = (HaveRouteException) e;
			meta = exc.getResult().getMeta();
		}
		ErrorRoutes errorRoutes = getErrorRoutes(requestCtx);
		routeLoader.processException(responseCb, requestCtx, e, errorRoutes, meta);
		return null;
	}
	
	protected MatchResult fetchRoute(RequestContext ctx) {
		MatchResult result = routeLoader.fetchRoute(ctx.getRequest());
		ctx.setPathParams(result.getPathParams());
		return result;
	}
	
	protected abstract ErrorRoutes getErrorRoutes(RequestContext ctx);
	protected abstract CompletableFuture<Void> incomingRequestImpl(RequestContext req, ResponseStreamer responseCb);
	
	@Override
	public String convertToUrl(String routeId, Map<String, String> args, boolean isValidating) {
		return routeLoader.convertToUrl(routeId, args, isValidating);
	}
	
	@Override
	public FileMeta relativeUrlToHash(String urlPath) {
		if(!urlPath.startsWith("/"))
			urlPath = "/"+urlPath;
		
		return routeLoader.relativeUrlToHash(urlPath);
	}
	
	protected void runStartupHooks(Injector injector) {
		log.info("Running startup hooks for server");
		
		Key<Set<Startable>> key = Key.get(new TypeLiteral<Set<Startable>>(){});
		Set<Startable> startupHooks = injector.getInstance(key);
		for(Startable s : startupHooks) {
			runStartupHook(s);
		}
		log.info("Ran all startup hooks");
	}

	private void runStartupHook(Startable s) {
		try {
			log.info("starting startup hook="+s.getClass().getSimpleName());
			s.start();
			log.info("Successfully ran startup hook="+s.getClass().getSimpleName());
		} catch(Throwable e) {
			throw new RuntimeException("Startup hook="+s.getClass().getSimpleName()+" failed", e);
		}
	}
	
	@Override
	public <T> ObjectStringConverter<T> getConverterFor(T bean) {
		return translator.getConverterFor(bean);
	}
}
