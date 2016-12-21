package org.webpieces.router.impl;

import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.webpieces.router.api.EntityLookup;
import org.webpieces.router.api.Startable;
import org.webpieces.router.impl.params.ParamToObjectTranslatorImpl;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

@Singleton
public class PluginSetup {

	private ParamToObjectTranslatorImpl translator;

	@Inject
	public PluginSetup(ParamToObjectTranslatorImpl translator) {
		this.translator = translator;
	}

	/**
	 * This is where we wire in all plugin points EXCEPT the Startup one
	 * @param injector
	 * @param startupFunction 
	 */
	public void wireInPluginPoints(Injector injector, Consumer<Injector> startupFunction) {

		Key<Set<EntityLookup>> key = Key.get(new TypeLiteral<Set<EntityLookup>>(){});
		Set<EntityLookup> lookupHooks = injector.getInstance(key);
		translator.install(lookupHooks);
		

		//wire in startup and start the startables.  This is a function since Dev and Production differ
		//in that Development we have to make sure we don't run startup code twice as it is likely to
		//blow up....or should we make this configurable?
		startupFunction.accept(injector);
	}

}