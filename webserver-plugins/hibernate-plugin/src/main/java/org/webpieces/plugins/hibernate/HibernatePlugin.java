package org.webpieces.plugins.hibernate;

import java.util.List;

import org.webpieces.router.api.routing.Plugin;
import org.webpieces.router.api.routing.RouteModule;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.inject.Module;

public class HibernatePlugin implements Plugin {

	private static final Logger log = LoggerFactory.getLogger(HibernatePlugin.class);
	
	public static final String PERSISTENCE_UNIT_KEY = "hibernate.persistenceunit.key";
	public static final String PERSISTENCE_TEST_UNIT = "hibernatefortest";
			
	private String persistenceUnit;

	public HibernatePlugin(String persistenceUnit) {
		log.info("classloader="+getClass().getClassLoader());
		this.persistenceUnit = persistenceUnit;
	}
	
	@Override
	public List<Module> getGuiceModules() {
		return Lists.newArrayList(new HibernateModule(persistenceUnit));
	}

	@Override
	public List<RouteModule> getRouteModules() {
		return Lists.newArrayList(new HibernateRouteModule());
	}

}