package eu.kanade.mangafeed;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Component;
import eu.kanade.mangafeed.data.DataModule;
import eu.kanade.mangafeed.presenter.CataloguePresenter;
import eu.kanade.mangafeed.presenter.SourcePresenter;
import eu.kanade.mangafeed.presenter.LibraryPresenter;
import eu.kanade.mangafeed.presenter.MangaDetailPresenter;

@Singleton
@Component(
        modules = {
                AppModule.class,
                DataModule.class
        }
)
public interface AppComponent {

    void inject(LibraryPresenter libraryPresenter);
    void inject(MangaDetailPresenter mangaDetailPresenter);
    void inject(SourcePresenter sourcePresenter);
    void inject(CataloguePresenter cataloguePresenter);

    Application application();
}
