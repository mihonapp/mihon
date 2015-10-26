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
    private Manga manga;

    private static final int DB_MANGA = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(DB_MANGA,
                () -> getDbMangaObservable()
                        .doOnNext(manga -> this.manga = manga),
                (view, manga) -> {
                    view.setManga(manga);
                    view.setFavoriteBtnVisible(!manga.favorite);
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
                .flatMap(Observable::from)
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void queryManga(long mangaId) {
        this.mangaId = mangaId;
        start(DB_MANGA);
    }

    public void setFavoriteVisibility() {
        if (getView() != null) {
            getView().setFavoriteBtnVisible(!manga.favorite);
        }
    }

    public boolean addToFavorites() {
        manga.favorite = true;
        return db.insertMangaBlock(manga).numberOfRowsUpdated() == 1;
    }
}
