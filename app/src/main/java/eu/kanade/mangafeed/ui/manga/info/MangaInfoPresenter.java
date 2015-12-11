package eu.kanade.mangafeed.ui.manga.info;

import android.os.Bundle;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.cache.CoverCache;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.event.ChapterCountEvent;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.EventBusHook;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MangaInfoPresenter extends BasePresenter<MangaInfoFragment> {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;
    @Inject CoverCache coverCache;

    private Manga manga;
    protected Source source;
    private int count = -1;

    private boolean isFetching;

    private static final int GET_MANGA = 1;
    private static final int GET_CHAPTER_COUNT = 2;
    private static final int FETCH_MANGA_INFO = 3;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            onProcessRestart();
        }

        restartableLatestCache(GET_MANGA,
                () -> Observable.just(manga),
                MangaInfoFragment::onNextManga);

        restartableLatestCache(GET_CHAPTER_COUNT,
                () -> Observable.just(count),
                MangaInfoFragment::setChapterCount);

        restartableFirst(FETCH_MANGA_INFO,
                this::fetchMangaObs,
                (view, manga) -> view.onFetchMangaDone(),
                (view, error) -> view.onFetchMangaError());

        registerForStickyEvents();
    }

    private void onProcessRestart() {
        stop(GET_MANGA);
        stop(GET_CHAPTER_COUNT);
        stop(FETCH_MANGA_INFO);
    }

    @Override
    protected void onDestroy() {
        unregisterForEvents();
        super.onDestroy();
    }

    @EventBusHook
    public void onEventMainThread(Manga manga) {
        this.manga = manga;
        source = sourceManager.get(manga.source);
        start(GET_MANGA);
    }

    @EventBusHook
    public void onEventMainThread(ChapterCountEvent event) {
        if (count != event.getCount()) {
            count = event.getCount();
            start(GET_CHAPTER_COUNT);
        }
    }

    public void fetchMangaFromSource() {
        if (!isFetching) {
            isFetching = true;
            start(FETCH_MANGA_INFO);
        }
    }

    private Observable<Manga> fetchMangaObs() {
        return source.pullMangaFromNetwork(manga.url)
                .flatMap(networkManga -> {
                    Manga.copyFromNetwork(manga, networkManga);
                    db.insertManga(manga).executeAsBlocking();
                    return Observable.just(manga);
                })
                .finallyDo(() -> isFetching = false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void toggleFavorite() {
        manga.favorite = !manga.favorite;
        onMangaFavoriteChange(manga.favorite);
        db.insertManga(manga).executeAsBlocking();
    }

    private void onMangaFavoriteChange(boolean isFavorite) {
        if (isFavorite) {
            coverCache.save(manga.thumbnail_url, source.getGlideHeaders());
        } else {
            coverCache.delete(manga.thumbnail_url);
        }
    }

}
