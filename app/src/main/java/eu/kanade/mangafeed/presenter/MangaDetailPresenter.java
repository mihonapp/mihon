package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.view.MangaDetailView;

public class MangaDetailPresenter extends BasePresenter2<MangaDetailActivity> {

    private MangaDetailView view;

    @Inject
    DatabaseHelper db;

    public void onEventMainThread(Manga manga) {
        view.loadManga(manga);
        initializeChapters(manga);
    }

    public void initializeChapters(Manga manga) {
        db.getChapters(manga)
                .subscribe(view::setChapters);
    }
}
