package eu.kanade.mangafeed.ui.manga.myanimelist;

import android.os.Bundle;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.database.models.MangaSync;
import eu.kanade.mangafeed.data.mangasync.MangaSyncManager;
import eu.kanade.mangafeed.data.mangasync.services.MyAnimeList;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.EventBusHook;
import eu.kanade.mangafeed.util.ToastUtil;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class MyAnimeListPresenter extends BasePresenter<MyAnimeListFragment> {

    @Inject DatabaseHelper db;
    @Inject MangaSyncManager syncManager;

    protected MyAnimeList myAnimeList;
    protected Manga manga;
    private MangaSync mangaSync;

    private String query;

    private Subscription updateSubscription;

    private static final int GET_CHAPTER_SYNC = 1;
    private static final int GET_SEARCH_RESULTS = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            onProcessRestart();
        }

        myAnimeList = syncManager.getMyAnimeList();

        restartableLatestCache(GET_CHAPTER_SYNC,
                () -> db.getMangaSync(manga, myAnimeList).createObservable()
                        .flatMap(Observable::from)
                        .doOnNext(mangaSync -> this.mangaSync = mangaSync)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread()),
                MyAnimeListFragment::setMangaSync);

        restartableLatestCache(GET_SEARCH_RESULTS,
                () -> myAnimeList.search(query)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread()),
                (view, results) -> {
                    view.onSearchResults(results);
                }, (view, error) -> {
                    Timber.e(error.getMessage());
                });

    }

    private void onProcessRestart() {
        stop(GET_CHAPTER_SYNC);
        stop(GET_SEARCH_RESULTS);
    }

    @Override
    protected void onTakeView(MyAnimeListFragment view) {
        super.onTakeView(view);
        registerForStickyEvents();
    }

    @Override
    protected void onDropView() {
        unregisterForEvents();
        super.onDropView();
    }

    @EventBusHook
    public void onEventMainThread(Manga manga) {
        this.manga = manga;
        start(GET_CHAPTER_SYNC);
    }

    public void updateLastChapter(int chapterNumber) {
        if (updateSubscription != null)
            remove(updateSubscription);

        mangaSync.last_chapter_read = chapterNumber;

        add(updateSubscription = myAnimeList.update(mangaSync)
                .flatMap(response -> db.insertMangaSync(mangaSync).createObservable())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(response -> {},
                        error -> {
                            Timber.e(error.getMessage());
                        }
                ));
    }

    public void searchManga(String query) {
        this.query = query;
        start(GET_SEARCH_RESULTS);
    }

    public void registerManga(MangaSync manga) {
        manga.manga_id = this.manga.id;
        add(myAnimeList.bind(manga)
                .flatMap(response -> {
                    if (response.code() == 200 || response.code() == 201)
                        return Observable.just(manga);
                    return Observable.error(new Exception("Could not add manga"));
                })
                .flatMap(manga2 -> db.insertMangaSync(manga2).createObservable())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(manga2 -> {},
                        error -> ToastUtil.showShort(getContext(), error.getMessage())));
    }

}
