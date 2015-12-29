package eu.kanade.mangafeed.ui.manga.myanimelist;

import android.content.Context;
import android.os.Bundle;

import javax.inject.Inject;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.database.models.MangaSync;
import eu.kanade.mangafeed.data.mangasync.MangaSyncManager;
import eu.kanade.mangafeed.data.mangasync.services.MyAnimeList;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.EventBusHook;
import eu.kanade.mangafeed.util.ToastUtil;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class MyAnimeListPresenter extends BasePresenter<MyAnimeListFragment> {

    @Inject DatabaseHelper db;
    @Inject MangaSyncManager syncManager;

    protected MyAnimeList myAnimeList;
    protected Manga manga;
    protected MangaSync mangaSync;

    private String query;

    private static final int GET_MANGA_SYNC = 1;
    private static final int GET_SEARCH_RESULTS = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            onProcessRestart();
        }

        myAnimeList = syncManager.getMyAnimeList();

        restartableLatestCache(GET_MANGA_SYNC,
                () -> db.getMangaSync(manga, myAnimeList).createObservable()
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
        stop(GET_MANGA_SYNC);
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
        start(GET_MANGA_SYNC);
    }

    private void updateRemote() {
        add(myAnimeList.update(mangaSync)
                .flatMap(response -> db.insertMangaSync(mangaSync).createObservable())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(next -> {},
                        error -> {
                            Timber.e(error.getMessage());
                            // Restart on error to set old values
                            start(GET_MANGA_SYNC);
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
                    if (response.isSuccessful()) {
                        return db.insertMangaSync(manga).createObservable();
                    }
                    return Observable.error(new Exception("Could not bind manga"));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(manga2 -> {},
                        error -> ToastUtil.showShort(getContext(), error.getMessage())));
    }

    public String[] getAllStatus(Context context) {
        return new String[] {
                context.getString(R.string.reading),
                context.getString(R.string.completed),
                context.getString(R.string.on_hold),
                context.getString(R.string.dropped),
                context.getString(R.string.plan_to_read)
        };
    }

    public int getIndexFromStatus() {
        return mangaSync.status == 6 ? 4 : mangaSync.status - 1;
    }

    public void setStatus(int index) {
        mangaSync.status = index == 4 ? 6 : index + 1;
        updateRemote();
    }

    public void setScore(int score) {
        mangaSync.score = score;
        updateRemote();
    }

    public void setLastChapterRead(int chapterNumber) {
        mangaSync.last_chapter_read = chapterNumber;
        updateRemote();
    }
}
