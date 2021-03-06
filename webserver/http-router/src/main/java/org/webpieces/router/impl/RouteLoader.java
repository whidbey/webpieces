package org.webpieces.router.impl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.webpieces.ctx.api.RequestContext;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.api.NeedsSimpleStorage;
import org.webpieces.router.api.ResponseStreamer;
import org.webpieces.router.api.RouterConfig;
import org.webpieces.router.api.SimpleStorage;
import org.webpieces.router.api.actions.Action;
import org.webpieces.router.api.dto.MethodMeta;
import org.webpieces.router.api.dto.RouteType;
import org.webpieces.router.api.routing.Plugin;
import org.webpieces.router.api.routing.Routes;
import org.webpieces.router.api.routing.WebAppMeta;
import org.webpieces.router.impl.compression.CompressionCacheSetup;
import org.webpieces.router.impl.compression.FileMeta;
import org.webpieces.router.impl.hooks.ClassForName;
import org.webpieces.router.impl.loader.ControllerLoader;
import org.webpieces.router.impl.mgmt.ManagedBeanMeta;
import org.webpieces.router.impl.model.AbstractRouteBuilder;
import org.webpieces.router.impl.model.L1AllRouting;
import org.webpieces.router.impl.model.L3PrefixedRouting;
import org.webpieces.router.impl.model.LogicHolder;
import org.webpieces.router.impl.model.MatchResult;
import org.webpieces.router.impl.model.R1RouterBuilder;
import org.webpieces.router.impl.model.RouteModuleInfo;
import org.webpieces.router.impl.model.RouterInfo;
import org.webpieces.router.impl.params.ObjectTranslator;
import org.webpieces.util.file.VirtualFile;
import org.webpieces.util.filters.Service;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class RouteLoader {
	private static final Logger log = LoggerFactory.getLogger(RouteLoader.class);
	
	private final RouterConfig config;
	private final RouteInvoker invoker;
	private final ControllerLoader controllerFinder;
	private CompressionCacheSetup compressionCacheSetup;
	
	protected R1RouterBuilder routerBuilder;

	private PluginSetup pluginSetup;

	private ManagedBeanMeta beanMeta;

	private ObjectTranslator objectTranslator;
	
	@Inject
	public RouteLoader(
		RouterConfig config, 
		RouteInvoker invoker,
		ControllerLoader controllerFinder,
		CompressionCacheSetup compressionCacheSetup,
		PluginSetup pluginSetup,
		ManagedBeanMeta beanMeta,
		ObjectTranslator objectTranslator
	) {
		this.config = config;
		this.invoker = invoker;
		this.controllerFinder = controllerFinder;
		this.compressionCacheSetup = compressionCacheSetup;
		this.pluginSetup = pluginSetup;
		this.beanMeta = beanMeta;
		this.objectTranslator = objectTranslator;
	}
	
	public WebAppMeta load(ClassForName loader, Consumer<Injector> startupFunction) {
		try {
			return loadImpl(loader, startupFunction);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private WebAppMeta loadImpl(ClassForName loader, Consumer<Injector> startupFunction) throws IOException {
		log.info("loading the master "+WebAppMeta.class.getSimpleName()+" class file");

		VirtualFile fileWithMetaClassName = config.getMetaFile();
		String moduleName;
		
		Charset fileEncoding = config.getFileEncoding();
		String contents = fileWithMetaClassName.contentAsString(fileEncoding);
		moduleName = contents.trim();
		
		log.info(WebAppMeta.class.getSimpleName()+" class to load="+moduleName);
		Class<?> clazz = loader.clazzForName(moduleName);
		
		//In development mode, the ClassLoader here will be the CompilingClassLoader so stuff it into the thread context
		//just in case plugins will need it(most won't, hibernate will).  In production, this is really a no-op and does
		//nothing.  I don't like dev code existing in production :( so perhaps we should abstract this out at some 
		//point perhaps with a lambda of a lambda function
		//OR have a DevelopmentRouteLoader subclass this ProdRouteLoader and add just his needed classloader lines!!! which
		//would be more clear
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(clazz.getClassLoader());
		
			Object obj = newInstance(clazz);
			if(!(obj instanceof WebAppMeta))
				throw new IllegalArgumentException("name="+moduleName+" does not implement "+WebAppMeta.class.getSimpleName());
	
			log.info(WebAppMeta.class.getSimpleName()+" loaded.  initializing next");
	
			RoutingHolder routingHolder = new RoutingHolder();
			
			WebAppMeta routerModule = (WebAppMeta) obj;
			routerModule.initialize(config.getWebAppMetaProperties());
			log.info(WebAppMeta.class.getSimpleName()+" initialized.");
			
			Injector injector = createInjector(routerModule, routingHolder);

			CompletableFuture<Void> storageLoadComplete = setupSimpleStorage(injector);
			
			pluginSetup.wireInPluginPoints(injector);
			
			loadAllRoutes(routerModule, injector, routingHolder);
			
			//wire in startup and start the startables.  This is a function since Dev and Production differ
			//in that Development we have to make sure we don't run startup code twice as it is likely to
			//blow up....or should we make this configurable?  ie. Dev may run on a recompile after starting up at
			//a later time and we most likely don't want to run startup code multiple times
			startupFunction.accept(injector);
			
			//We wait for the storage load next as that has to be complete before the router startup is finished!!!!
			//BUT notice we wait AFTER load of routes so all that is done in parallel
			//YES, I could nit and make this async BUT KISS can be better sometimes and our startup is quit fast right
			//now so let's not pre-optimize.  Also, the default implementation is synchronous anyways right now since
			//default JDBC is synchronous
			storageLoadComplete.get(3, TimeUnit.SECONDS);
			
			return routerModule;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private CompletableFuture<Void> setupSimpleStorage(Injector injector) {
		SimpleStorage storage = injector.getInstance(SimpleStorage.class);
		
		
		
		NeedsSimpleStorage needsStorage = config.getNeedsStorage();
		if(needsStorage != null)
			return needsStorage.init(storage);
		else
			return CompletableFuture.completedFuture(null);
	}

	public Injector createInjector(WebAppMeta routerModule, RoutingHolder routingHolder) {
		List<Module> guiceModules = routerModule.getGuiceModules();
		if(guiceModules == null)
			guiceModules = new ArrayList<>();
		
		guiceModules.add(new EmptyPluginModule(routingHolder, beanMeta, objectTranslator));
		
		Module module = Modules.combine(guiceModules);
		
		List<Plugin> plugins = routerModule.getPlugins();
		if(plugins != null) {
			for(Plugin plugin : plugins) {
				List<Module> modules = new ArrayList<>();
				modules.addAll(plugin.getGuiceModules());
				modules.add(module);
				module = Modules.combine(modules);
			}
		}

		if(config.getWebappOverrides() != null)
			module = Modules.override(module).with(config.getWebappOverrides());
		
		Injector injector = Guice.createInjector(module);
		return injector;
	}

	//protected abstract void verifyRoutes(Collection<Route> allRoutes);

	public void loadAllRoutes(WebAppMeta rm, Injector injector, RoutingHolder routingHolder) {
		log.info("adding routes");
		
		ReverseRoutes reverseRoutes = new ReverseRoutes(config);
		//routerBuilder = new RouterBuilder("", new AllRoutingInfo(), reverseRoutes, controllerFinder, config.getUrlEncoding());
		LogicHolder holder = new LogicHolder(reverseRoutes, controllerFinder, injector, config);
		routerBuilder = new R1RouterBuilder(new RouterInfo(null, ""), new L1AllRouting(), holder, false);
		routingHolder.setRouterBuilder(routerBuilder);
		routingHolder.setReverseRouteLookup(reverseRoutes);
		invoker.init(reverseRoutes);
		
		List<Routes> all = new ArrayList<>();
		all.addAll(rm.getRouteModules()); //the core application routes
		
		List<Plugin> plugins = rm.getPlugins();
		if(plugins != null) {
			for(Plugin plugin : plugins) {
				all.addAll(plugin.getRouteModules());
			}
		}
		
		for(Routes module : all) {
			AbstractRouteBuilder.currentPackage.set(new RouteModuleInfo(module));
			module.configure(routerBuilder);
			AbstractRouteBuilder.currentPackage.set(null);
		}
		
		log.info("added all routes to router.  Applying Filters");

		reverseRoutes.finalSetup();
		
		routerBuilder.applyFilters(rm);
		
		Collection<RouteMeta> metas = reverseRoutes.getAllRouteMetas();
		for(RouteMeta m : metas) {
			controllerFinder.loadFiltersIntoMeta(m, m.getFilters(), true);
		}
		
		routerBuilder.loadNotFoundAndErrorFilters();
	
		log.info("all filters applied");
		
		compressionCacheSetup.setupCache(routerBuilder.getStaticRoutes());
	}

	private Object newInstance(Class<?> clazz) {
		try {
			return clazz.newInstance();
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Your clazz="+clazz.getSimpleName()+" could not be created(are you missing default constructor? is it not public?)", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Your clazz="+clazz.getSimpleName()+" could not be created", e);
		}
	}

	public MatchResult fetchRoute(RouterRequest req) {
		L1AllRouting allRoutingInfo = routerBuilder.getRouterInfo();
		MatchResult meta = allRoutingInfo.fetchRoute(req, req.relativePath);
		if(meta == null)
			throw new IllegalStateException("missing exception on creation if we go this far");

		return meta;
	}
	
	public CompletableFuture<Void> invokeRoute(MatchResult result, RequestContext routerRequest, ResponseStreamer responseCb, ErrorRoutes errorRoutes) {
		//This class is purely the RouteLoader so delegate and encapsulate the invocation in RouteInvoker....
		return invoker.invoke(result, routerRequest, responseCb, errorRoutes);
	}

	public Void processException(ResponseStreamer responseCb, RequestContext requestCtx, Throwable e, ErrorRoutes errorRoutes, Object meta) {
		return invoker.processException(responseCb, requestCtx, e, errorRoutes, meta);
	}
	
	public RouteMeta fetchNotFoundRoute(String domain) {
		L1AllRouting routerInfo = routerBuilder.getRouterInfo();
		RouteMeta notfoundRoute = routerInfo.getPageNotfoundRoute(domain);
		return notfoundRoute;
	}

	public RouteMeta fetchInternalErrorRoute(String domain) {
		L1AllRouting routerInfo = routerBuilder.getRouterInfo();
		RouteMeta internalErrorRoute = routerInfo.getInternalErrorRoute(domain);
		return internalErrorRoute;
	}

	public  Service<MethodMeta, Action> createNotFoundService(RouteMeta m, RouterRequest req) {
		List<FilterInfo<?>> filterInfos = routerBuilder.findNotFoundFilters(req.relativePath, req.isHttps);
		return controllerFinder.createNotFoundService(m, filterInfos);
	}

	public String convertToUrl(String routeId, Map<String, String> args, boolean isValidating) {
		return invoker.convertToUrl(routeId, args, isValidating);
	}
	
	public FileMeta relativeUrlToHash(String urlPath) {
		return compressionCacheSetup.relativeUrlToHash(urlPath);
	}


	public void printAllRoutes(Route route) {
		if(!log.isDebugEnabled())
			return;
		else if(route.getRouteType() != RouteType.NOT_FOUND)
			return;

		L1AllRouting routingInfo = routerBuilder.getRouterInfo();
		
		//TODO: domain specific routes
		//Collection<L2DomainRoutes> domains = routingInfo.getSpecificDomains();

		L3PrefixedRouting mainRoutes = routingInfo.getMainRoutes().getRoutesForDomain();
		mainRoutes.printRoutes(route.isHttpsRoute(), "");
	}
}
