package eu.kanade.mangafeed.injection.component;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Component;
import eu.kanade.mangafeed.data.download.DownloadService;
import eu.kanade.mangafeed.data.mangasync.services.MyAnimeList;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.sync.LibraryUpdateService;
import eu.kanade.mangafeed.data.sync.UpdateMangaSyncService;
import eu.kanade.mangafeed.injection.module.AppModule;
import eu.kanade.mangafeed.injection.module.DataModule;
import eu.kanade.mangafeed.ui.catalogue.CataloguePresenter;
import eu.kanade.mangafeed.ui.download.DownloadPresenter;
import eu.kanade.mangafeed.ui.library.category.CategoryPresenter;
import eu.kanade.mangafeed.ui.library.LibraryPresenter;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import eu.kanade.mangafeed.ui.manga.MangaPresenter;
import eu.kanade.mangafeed.ui.manga.chapter.ChaptersPresenter;
import eu.kanade.mangafeed.ui.manga.info.MangaInfoPresenter;
import eu.kanade.mangafeed.ui.manga.myanimelist.MyAnimeListPresenter;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.ReaderPresenter;
import eu.kanade.mangafeed.ui.setting.SettingsAccountsFragment;
import eu.kanade.mangafeed.ui.setting.SettingsActivity;

@Singleton
@Component(
        modules = {
                AppModule.class,
                DataModule.class
        }
)
public interface AppComponent {

    void inject(LibraryPresenter libraryPresenter);
    void inject(MangaPresenter mangaPresenter);
    void inject(CataloguePresenter cataloguePresenter);
    void inject(MangaInfoPresenter mangaInfoPresenter);
    void inject(ChaptersPresenter chaptersPresenter);
    void inject(ReaderPresenter readerPresenter);
    void inject(DownloadPresenter downloadPresenter);
    void inject(MyAnimeListPresenter myAnimeListPresenter);
    void inject(CategoryPresenter categoryPresenter);

    void inject(ReaderActivity readerActivity);
    void inject(MangaActivity mangaActivity);
    void inject(SettingsAccountsFragment settingsAccountsFragment);
    void inject(SettingsActivity settingsActivity);

    void inject(Source source);

    void inject(MyAnimeList myAnimeList);

    void inject(LibraryUpdateService libraryUpdateService);
    void inject(DownloadService downloadService);
    void inject(UpdateMangaSyncService updateMangaSyncService);

    Application application();

}
