package org.webpieces.router.impl;

import org.webpieces.router.api.BodyContentBinder;
import org.webpieces.router.api.EntityLookup;
import org.webpieces.router.api.ObjectStringConverter;
import org.webpieces.router.api.Startable;
import org.webpieces.router.impl.mgmt.ManagedBeanMeta;
import org.webpieces.router.impl.params.ObjectTranslator;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

public class EmptyPluginModule implements Module {

	private RoutingHolder routingHolder;
	private ManagedBeanMeta managedMeta;
	private ObjectTranslator objectTranslator;

	public EmptyPluginModule(RoutingHolder routingHolder, ManagedBeanMeta managedMeta, ObjectTranslator objectTranslator) {
		this.routingHolder = routingHolder;
		this.managedMeta = managedMeta;
		this.objectTranslator = objectTranslator;
	}

	@Override
	public void configure(Binder binder) {
		//creates an empty binder in case app installs ZERO plugins
		Multibinder.newSetBinder(binder, Startable.class);
		Multibinder.newSetBinder(binder, EntityLookup.class);
		Multibinder.newSetBinder(binder, BodyContentBinder.class);
		Multibinder.newSetBinder(binder, ObjectStringConverter.class);
		
		//special case so the notFound controller can inpsect and list all routes in a web page
		//OR some client application can inject and introspect all web routes as well
		//OR some plugin on startup can look at all routes as well
		//Also, special case so the backend plugin can accept route ids and reverse lookup the URL on them
		binder.bind(RoutingHolder.class).toInstance(routingHolder);

		//These next two bindings are specifically used by the properties plugin but could be used by any other plugins as well
		
		//special case exposing webpieces managed beans for modification to any plugin that wants to use it
		binder.bind(ManagedBeanMeta.class).toInstance(managedMeta);
		//special case exposing webpieces + application converters (object to string and string to object)
		//This is the lookup class for all those ObjectStringConverter that get installed in the above Multibinder
		binder.bind(ObjectTranslator.class).toInstance(objectTranslator);
		
		//well, hot damn, everything is like this special case
	}
}
