package eu.kanade.tachiyomi.ui.manga.info;

import android.os.Bundle;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import javax.inject.Inject;

import eu.kanade.tachiyomi.data.cache.CoverCache;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.SourceManager;
import eu.kanade.tachiyomi.data.source.base.Source;
import eu.kanade.tachiyomi.event.ChapterCountEvent;
import eu.kanade.tachiyomi.event.MangaEvent;
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Presenter of MangaInfoFragment.
 * Contains information and data for fragment.
 * Observable updates should be called from here.
 */
public class MangaInfoPresenter extends BasePresenter<MangaInfoFragment> {

    /**
     * The id of the restartable.
     */
    private static final int GET_MANGA = 1;

    /**
     * The id of the restartable.
     */
    private static final int GET_CHAPTER_COUNT = 2;

    /**
     * The id of the restartable.
     */
    private static final int FETCH_MANGA_INFO = 3;

    /**
     * Source information.
     */
    protected Source source;

    /**
     * Used to connect to database.
     */
    @Inject DatabaseHelper db;

    /**
     * Used to connect to different manga sources.
     */
    @Inject SourceManager sourceManager;

    /**
     * Used to connect to cache.
     */
    @Inject CoverCache coverCache;

    /**
     * Selected manga information.
     */
    private Manga manga;

    /**
     * Count of chapters.
     */
    private int count = -1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Notify the view a manga is available or has changed.
        startableLatestCache(GET_MANGA,
                () -> Observable.just(manga),
                (view, manga) -> view.onNextManga(manga, source));

        // Update chapter count.
        startableLatestCache(GET_CHAPTER_COUNT,
                () -> Observable.just(count),
                MangaInfoFragment::setChapterCount);

        // Fetch manga info from source.
        startableFirst(FETCH_MANGA_INFO,
                this::fetchMangaObs,
                (view, manga) -> view.onFetchMangaDone(),
                (view, error) -> view.onFetchMangaError());

        // Listen for events.
        registerForEvents();
    }

    @Override
    protected void onDestroy() {
        unregisterForEvents();
        super.onDestroy();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(MangaEvent event) {
        this.manga = event.manga;
        source = sourceManager.get(manga.source);
        refreshManga();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(ChapterCountEvent event) {
        if (count != event.getCount()) {
            count = event.getCount();
            // Update chapter count
            start(GET_CHAPTER_COUNT);
        }
    }

    /**
     * Fetch manga information from source.
     */
    public void fetchMangaFromSource() {
        if (isUnsubscribed(FETCH_MANGA_INFO)) {
            start(FETCH_MANGA_INFO);
        }
    }

    /**
     * Fetch manga information from source.
     *
     * @return manga information.
     */
    private Observable<Manga> fetchMangaObs() {
        return source.pullMangaFromNetwork(manga.url)
                .flatMap(networkManga -> {
                    manga.copyFrom(networkManga);
                    db.insertManga(manga).executeAsBlocking();
                    return Observable.just(manga);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(manga -> refreshManga());
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    public void toggleFavorite() {
        manga.favorite = !manga.favorite;
        onMangaFavoriteChange(manga.favorite);
        db.insertManga(manga).executeAsBlocking();
        refreshManga();
    }


    /**
     * (Removes / Saves) cover depending on favorite status.
     *
     * @param isFavorite determines if manga is favorite or not.
     */
    private void onMangaFavoriteChange(boolean isFavorite) {
        if (isFavorite) {
            coverCache.save(manga.thumbnail_url, source.getGlideHeaders());
        } else {
            coverCache.deleteCoverFromCache(manga.thumbnail_url);
        }
    }

    /**
     * Refresh MangaInfo view.
     */
    private void refreshManga() {
        start(GET_MANGA);
    }

}
