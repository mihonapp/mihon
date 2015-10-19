package eu.kanade.mangafeed.presenter;

import android.os.Bundle;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MangaDetailPresenter extends BasePresenter<MangaDetailActivity> {

    @Inject DatabaseHelper db;

    private long mangaId;
    private static final int DB_MANGA = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(DB_MANGA,
                this::getDbMangaObservable,
                (view, manga) -> {
                    view.setManga(manga);
                    EventBus.getDefault().postSticky(manga);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Avoid fragments receiving wrong manga
        EventBus.getDefault().removeStickyEvent(Manga.class);
    }

    private Observable<Manga> getDbMangaObservable() {
        return db.getManga(mangaId)
                .subscribeOn(Schedulers.io())
                .take(1)
                .flatMap(Observable::from)
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void queryManga(long mangaId) {
        this.mangaId = mangaId;
        start(DB_MANGA);
    }

}
