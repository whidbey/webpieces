package WEBPIECESxPACKAGE.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.plugins.backend.login.BackendLogin;
import org.webpieces.router.api.ObjectStringConverter;
import org.webpieces.router.api.SimpleStorage;
import org.webpieces.router.api.Startable;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

import WEBPIECESxPACKAGE.base.crud.login.BackendLoginImpl;
import WEBPIECESxPACKAGE.base.libs.EducationEnum;
import WEBPIECESxPACKAGE.base.libs.RemoteService;
import WEBPIECESxPACKAGE.base.libs.RemoteServiceImpl;
import WEBPIECESxPACKAGE.base.libs.RoleEnum;
import WEBPIECESxPACKAGE.base.libs.SimpleStorageImpl;
import WEBPIECESxPACKAGE.base.libs.SomeLibrary;
import WEBPIECESxPACKAGE.base.libs.SomeLibraryImpl;

public class GuiceModule implements Module {

	private static final Logger log = LoggerFactory.getLogger(GuiceModule.class);
	
	//This is where you would put the guice bindings you need though generally if done
	//right, you won't have much in this file.
	
	//If you need more Guice Modules as you want to scale, just modify WEBPIECESxCLASSMeta which returns
	//the list of all the Guice Modules in your application
	@SuppressWarnings("rawtypes")
	@Override
	public void configure(Binder binder) {
		
		log.info("running module");
		
		//all modules have access to adding their own Startable objects to be run on server startup
		Multibinder<Startable> uriBinder = Multibinder.newSetBinder(binder, Startable.class);
	    uriBinder.addBinding().to(PopulateDatabase.class);

		Multibinder<ObjectStringConverter> conversionBinder = Multibinder.newSetBinder(binder, ObjectStringConverter.class);
		conversionBinder.addBinding().to(EducationEnum.WebConverter.class);
		conversionBinder.addBinding().to(RoleEnum.WebConverter.class);
	    
	    binder.bind(SomeLibrary.class).to(SomeLibraryImpl.class);
	    binder.bind(RemoteService.class).to(RemoteServiceImpl.class).asEagerSingleton();

	    //Must bind a SimpleStorage for plugins to read/save data and render their html pages
	    binder.bind(SimpleStorage.class).to(SimpleStorageImpl.class).asEagerSingleton();
	    
	    //Must bind a BackendLogin for the backend plugin(or remove the backend plugin)
	    binder.bind(BackendLogin.class).to(BackendLoginImpl.class).asEagerSingleton();
	}

}
