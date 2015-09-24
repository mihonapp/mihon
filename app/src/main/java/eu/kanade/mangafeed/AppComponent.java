package eu.kanade.mangafeed;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Component;
import eu.kanade.mangafeed.data.DataModule;
import eu.kanade.mangafeed.ui.activity.MainActivity;

@Singleton
@Component(
        modules = {
                AppModule.class,
                DataModule.class
        }
)
public interface AppComponent {

    void inject(MainActivity mainActivity);

    Application application();
}