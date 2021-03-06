package org.webpieces.plugins.hibernate.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.webpieces.plugins.hibernate.HibernateConfig;
import org.webpieces.plugins.hibernate.HibernatePlugin;
import org.webpieces.plugins.hibernate.app.ajax.AjaxHibernateCrudRoutes;
import org.webpieces.router.api.SimpleStorage;
import org.webpieces.router.api.routing.Plugin;
import org.webpieces.router.api.routing.Routes;
import org.webpieces.router.api.routing.WebAppMeta;
import org.webpieces.webserver.EmptyStorage;

import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Module;

public class HibernateAppMeta implements WebAppMeta {
	public static final String PERSISTENCE_TEST_UNIT = "webpieces-persistence";

	@Override
	public void initialize(Map<String, String> props) {
	}
	
    @Override
    public List<Module> getGuiceModules() {
		return Lists.newArrayList(new AppModule());
	}
	
	@Override
    public List<Routes> getRouteModules() {
		return Lists.newArrayList(
				new HibernateTestRoutes(),
				new AjaxHibernateCrudRoutes());
	}
	
	@Override
	public List<Plugin> getPlugins() {
		return Lists.<Plugin>newArrayList(
				new HibernatePlugin(new HibernateConfig(PERSISTENCE_TEST_UNIT)));
	}

	private class AppModule implements Module {
		@Override
		public void configure(Binder binder) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			binder.bind(Executor.class).toInstance(executor);
			
			binder.bind(SimpleStorage.class).toInstance(new EmptyStorage());
		}
	}
}