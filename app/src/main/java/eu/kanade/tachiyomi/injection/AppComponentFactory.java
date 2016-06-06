package eu.kanade.tachiyomi.injection;

import eu.kanade.tachiyomi.App;
import eu.kanade.tachiyomi.injection.component.AppComponent;
import eu.kanade.tachiyomi.injection.component.DaggerAppComponent;
import eu.kanade.tachiyomi.injection.module.AppModule;


public class AppComponentFactory {

    public static AppComponent create(App app) {
        return DaggerAppComponent.builder().appModule(new AppModule(app)).build();
    }
}


