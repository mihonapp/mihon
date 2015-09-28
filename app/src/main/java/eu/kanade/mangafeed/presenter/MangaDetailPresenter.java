package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.view.MangaDetailView;

public class MangaDetailPresenter {

    private MangaDetailView view;

    @Inject
    DatabaseHelper db;

    public MangaDetailPresenter(MangaDetailView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void onStart() {
        EventBus.getDefault().registerSticky(this);
    }

    public void onStop() {
        EventBus.getDefault().unregister(this);
    }

    public void onEventMainThread(Manga manga) {
        view.loadManga(manga);
        initializeChapters(manga);
    }

    public static void newIntent(Manga manga) {
        EventBus.getDefault().postSticky(manga);
    }

    public void initializeChapters(Manga manga) {
        db.chapter.get(manga)
                .subscribe(view::setChapters);
    }
}
