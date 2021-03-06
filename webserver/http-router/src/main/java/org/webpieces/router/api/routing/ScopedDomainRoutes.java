package org.webpieces.router.api.routing;

import java.util.Arrays;
import java.util.List;

import org.webpieces.router.impl.model.AbstractRouteBuilder;
import org.webpieces.router.impl.model.RouteModuleInfo;

public class ScopedDomainRoutes extends AbstractRoutes {

	private String domain;
	private List<Routes> modules;

	public ScopedDomainRoutes(String domain, Routes ... modules) {
		if(domain == null || domain.length() == 0)
			throw new IllegalArgumentException("domain cannot be null and must be larger than size 0");
		this.domain = domain;
		this.modules = Arrays.asList(modules);
	}

	@Override
	protected void configure() {
		this.router = router.getDomainScopedRouter(domain);
		
		for(Routes module : modules) {
			AbstractRouteBuilder.currentPackage.set(new RouteModuleInfo(module));
			module.configure(router);
			AbstractRouteBuilder.currentPackage.set(null);
		}
	}
	
	@Override
	public String toString() {
		return "ScopedDomainModule [domain=" + domain + ", modules=" + modules + "]";
	}
}
