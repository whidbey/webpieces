package org.webpieces.router.impl.ctx;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.webpieces.ctx.api.Current;
import org.webpieces.ctx.api.HttpMethod;
import org.webpieces.ctx.api.RequestContext;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.api.PortConfig;
import org.webpieces.router.api.ResponseStreamer;
import org.webpieces.router.api.actions.RenderContent;
import org.webpieces.router.api.dto.RedirectResponse;
import org.webpieces.router.api.dto.RenderContentResponse;
import org.webpieces.router.api.dto.RenderResponse;
import org.webpieces.router.api.dto.RenderStaticResponse;
import org.webpieces.router.api.dto.RouteType;
import org.webpieces.router.api.dto.View;
import org.webpieces.router.api.exceptions.IllegalReturnValueException;
import org.webpieces.router.api.routing.RouteId;
import org.webpieces.router.impl.ReverseRoutes;
import org.webpieces.router.impl.Route;
import org.webpieces.router.impl.RouteMeta;
import org.webpieces.router.impl.actions.AjaxRedirectImpl;
import org.webpieces.router.impl.actions.RawRedirect;
import org.webpieces.router.impl.actions.RedirectImpl;
import org.webpieces.router.impl.actions.RenderImpl;
import org.webpieces.router.impl.params.ObjectToParamTranslator;

public class ResponseProcessor {
	
	private ReverseRoutes reverseRoutes;
	private RouteMeta matchedMeta;
	private ObjectToParamTranslator reverseTranslator;
	private RequestContext ctx;
	private ResponseStreamer responseCb;

	private boolean responseSent = false;
	private PortConfig portConfig;

	public ResponseProcessor(RequestContext ctx, ReverseRoutes reverseRoutes, 
			ObjectToParamTranslator reverseTranslator, RouteMeta meta, ResponseStreamer responseCb, PortConfig portConfig) {
		this.ctx = ctx;
		this.reverseRoutes = reverseRoutes;
		this.reverseTranslator = reverseTranslator;
		this.matchedMeta = meta;
		this.responseCb = responseCb;
		this.portConfig = portConfig;
	}

	public CompletableFuture<Void> createRawRedirect(RawRedirect controllerResponse) {
		String url = controllerResponse.getUrl();
		if(url.startsWith("http")) {
			return wrapFunctionInContext(() -> responseCb.sendRedirect(new RedirectResponse(url)));
		}

		RouterRequest request = ctx.getRequest();
		RedirectResponse redirectResponse = new RedirectResponse(false, request.isHttps, request.domain, request.port, url);
		return wrapFunctionInContext(() -> responseCb.sendRedirect(redirectResponse));
	}
	

	
	public CompletableFuture<Void> createAjaxRedirect(AjaxRedirectImpl action) {
		RouteId id = action.getId();
		Map<String, Object> args = action.getArgs();
		return createRedirect(id, args, true);		
	}
	
	public CompletableFuture<Void> createFullRedirect(RedirectImpl action) {
		RouteId id = action.getId();
		Map<String, Object> args = action.getArgs();
		return createRedirect(id, args, false);
	}
	
	private CompletableFuture<Void> createRedirect(RouteId id, Map<String, Object> args, boolean isAjaxRedirect) {
		if(responseSent)
			throw new IllegalStateException("You already sent a response.  do not call Actions.redirect or Actions.render more than once");
		responseSent = true;
		RouterRequest request = ctx.getRequest();
		Method method = matchedMeta.getMethod();
		RouteMeta nextRequestMeta = reverseRoutes.get(id);
		
		if(nextRequestMeta == null)
			throw new IllegalReturnValueException("Route="+id+" returned from method='"+method+"' was not added in the RouterModules");
		else if(!nextRequestMeta.getRoute().matchesMethod(HttpMethod.GET))
			throw new IllegalReturnValueException("method='"+method+"' is trying to redirect to routeid="+id+" but that route is not a GET method route and must be");

		Route route = nextRequestMeta.getRoute();
		
		Map<String, String> keysToValues = reverseTranslator.formMap(method, route.getPathParamNames(), args);

		Set<String> keySet = keysToValues.keySet();
		List<String> argNames = route.getPathParamNames();
		if(keySet.size() != argNames.size()) {
			throw new IllegalReturnValueException("Method='"+method+"' returns a Redirect action with wrong number of arguments.  args="+keySet.size()+" when it should be size="+argNames.size());
		}

		String path = route.getFullPath();
		
		for(String name : argNames) {
			String value = keysToValues.get(name);
			if(value == null) 
				throw new IllegalArgumentException("Method='"+method+"' returns a Redirect that is missing argument key="+name+" to form the url on the redirect");
			path = path.replace("{"+name+"}", value);
		}
		
		//if the request is https, stay in https as everything is accessible on https
		//if the request is http, then convert to https IF new route is secure
		boolean isSecure = request.isHttps || route.isHttpsRoute();
		int port = request.port;
		//if need to change port to https port, this is how we do it...
		if(!request.isHttps && route.isHttpsRoute())
			port = portConfig.getHttpsPort();
		
		RedirectResponse redirectResponse = new RedirectResponse(isAjaxRedirect, isSecure, request.domain, port, path);
		
		return wrapFunctionInContext(() -> responseCb.sendRedirect(redirectResponse));
	}

	public CompletableFuture<Void> createRenderResponse(RenderImpl controllerResponse) {
		if(responseSent)
			throw new IllegalStateException("You already sent a response.  do not call Actions.redirect or Actions.render more than once");
		responseSent = true;
		
		RouterRequest request = ctx.getRequest();

		Method method = matchedMeta.getMethod();
		//in the case where the POST route was found, the controller canNOT be returning RenderHtml and should follow PRG
		//If the POST route was not found, just render the notFound page that controller sends us violating the
		//PRG pattern in this one specific case for now (until we test it with the browser to make sure back button is
		//not broken)
		if(matchedMeta.getRoute().getRouteType() == RouteType.HTML && HttpMethod.POST == request.method) {
			throw new IllegalReturnValueException("Controller method='"+method+"' MUST follow the PRG "
					+ "pattern(https://en.wikipedia.org/wiki/Post/Redirect/Get) so "
					+ "users don't have a poor experience using your website with the browser back button.  "
					+ "This means on a POST request, you cannot return RenderHtml object and must return Redirects");
		}

		String controllerName = matchedMeta.getControllerInstance().getClass().getName();
		String methodName = matchedMeta.getMethod().getName();
		
		String relativeOrAbsolutePath = controllerResponse.getRelativeOrAbsolutePath();
		if(relativeOrAbsolutePath == null) {
			relativeOrAbsolutePath = methodName+".html";
		}

		Map<String, Object> pageArgs = controllerResponse.getPageArgs();

        // Add context as a page arg:
        pageArgs.put("_context", ctx);
        pageArgs.put("_session", ctx.getSession());
        pageArgs.put("_flash", ctx.getFlash());

		View view = new View(controllerName, methodName, relativeOrAbsolutePath);
		RenderResponse resp = new RenderResponse(view, pageArgs, matchedMeta.getRoute().getRouteType());
		
		return wrapFunctionInContext(() -> responseCb.sendRenderHtml(resp));
	}

	private CompletableFuture<Void> wrapFunctionInContext(Supplier<CompletableFuture<Void>> function) {
		boolean wasSet = Current.isContextSet();
		if(!wasSet)
			Current.setContext(ctx); //Allow html tags to use the contexts
		try {
			CompletableFuture<Void> future = function.get();
			return future;
		} finally {
			if(!wasSet) //then reset
				Current.setContext(null);
		}
	}

	public CompletableFuture<Void> failureRenderingInternalServerErrorPage(Throwable e) {
		return wrapFunctionInContext(() -> responseCb.failureRenderingInternalServerErrorPage(e));
	}

	public CompletableFuture<Void> renderStaticResponse(RenderStaticResponse renderStatic) {
		boolean wasSet = Current.isContextSet();
		if(!wasSet)
			Current.setContext(ctx); //Allow html tags to use the contexts
		try {
			return responseCb.sendRenderStatic(renderStatic);
		} finally {
			if(!wasSet) //then reset
				Current.setContext(null);
		}
	}

	public CompletableFuture<Void> createContentResponse(RenderContent r) {
		if(responseSent)
			throw new IllegalStateException("You already sent a response.  do not call Actions.redirect or Actions.render more than once");

		RenderContentResponse resp = new RenderContentResponse(r.getContent(), r.getStatusCode(), r.getReason(), r.getMimeType());
		return wrapFunctionInContext(() -> responseCb.sendRenderContent(resp));
	}
	
}
