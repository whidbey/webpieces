package org.webpieces.webserver.domains.app;

import java.util.List;
import java.util.Map;

import org.webpieces.router.api.routing.Plugin;
import org.webpieces.router.api.routing.Routes;
import org.webpieces.router.api.routing.ScopedDomainRoutes;
import org.webpieces.router.api.routing.WebAppMeta;
import org.webpieces.webserver.EmptyModule;

import com.google.common.collect.Lists;
import com.google.inject.Module;

public class DomainsMeta implements WebAppMeta {
	@Override
	public void initialize(Map<String, String> props) {
	}
	@Override
    public List<Module> getGuiceModules() {
		return Lists.newArrayList(new EmptyModule());
	}
	
	@Override
    public List<Routes> getRouteModules() {
		Routes domainModule = new ScopedDomainRoutes("mydomain.com", new Domain1Routes());

		return Lists.newArrayList(domainModule, new Domain2Routes());
	}
	
	@Override
	public List<Plugin> getPlugins() {
		return null;
	}
}