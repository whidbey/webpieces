package org.webpieces.plugins.documentation;

import java.util.List;

import org.webpieces.router.api.routing.Plugin;
import org.webpieces.router.api.routing.Routes;

import com.google.common.collect.Lists;
import com.google.inject.Module;

public class WebpiecesDocumentationPlugin implements Plugin {

	private DocumentationConfig config;

	public WebpiecesDocumentationPlugin(DocumentationConfig config) {
		super();
		this.config = config;
	}
	
	@Override
	public List<Module> getGuiceModules() {
		return Lists.newArrayList(new DocumentationModule(config));
	}

	@Override
	public List<Routes> getRouteModules() {
		return Lists.newArrayList(
			new DocumentationRoutes(config)
		);
	}

}
