package org.webpieces.router.impl;

import java.util.List;
import java.util.regex.Matcher;

import org.webpieces.ctx.api.HttpMethod;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.api.dto.RouteType;

public interface Route {

	String getFullPath();

	boolean matchesMethod(HttpMethod method);
	
	Matcher matches(RouterRequest request, String subPath);

	String getControllerMethodString();

	List<String> getPathParamNames();

	RouteType getRouteType();

	boolean isPostOnly();

	boolean isCheckSecureToken();

	boolean isHttpsRoute();

	HttpMethod getHttpMethod();
	
	String getMethod();
}
