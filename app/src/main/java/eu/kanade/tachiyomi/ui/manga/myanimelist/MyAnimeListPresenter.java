package eu.kanade.tachiyomi.ui.manga.myanimelist;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import javax.inject.Inject;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaSync;
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager;
import eu.kanade.tachiyomi.data.mangasync.services.MyAnimeList;
import eu.kanade.tachiyomi.event.MangaEvent;
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter;
import eu.kanade.tachiyomi.util.ToastUtil;
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
    private static final int REFRESH = 3;

    private static final String PREFIX_MY = "my:";

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        myAnimeList = syncManager.getMyAnimeList();

        startableLatestCache(GET_MANGA_SYNC,
                () -> db.getMangaSync(manga, myAnimeList).asRxObservable()
                        .doOnNext(mangaSync -> this.mangaSync = mangaSync)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread()),
                MyAnimeListFragment::setMangaSync);

        startableLatestCache(GET_SEARCH_RESULTS,
                this::getSearchResultsObservable,
                (view, results) -> {
                    view.setSearchResults(results);
                }, (view, error) -> {
                    Timber.e(error.getMessage());
                    view.setSearchResultsError();
                });

        startableFirst(REFRESH,
                () -> myAnimeList.getList()
                        .flatMap(myList -> {
                            for (MangaSync myManga : myList) {
                                if (myManga.remote_id == mangaSync.remote_id) {
                                    mangaSync.copyPersonalFrom(myManga);
                                    mangaSync.total_chapters = myManga.total_chapters;
                                    return Observable.just(mangaSync);
                                }
                            }
                            return Observable.error(new Exception("Could not find manga"));
                        })
                        .flatMap(myManga -> db.insertMangaSync(myManga).asRxObservable())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread()),
                (view, result) -> view.onRefreshDone(),
                (view, error) -> view.onRefreshError());

    }

    @Override
    protected void onTakeView(MyAnimeListFragment view) {
        super.onTakeView(view);
        registerForEvents();
    }

    @Override
    protected void onDropView() {
        unregisterForEvents();
        super.onDropView();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(MangaEvent event) {
        this.manga = event.manga;
        start(GET_MANGA_SYNC);
    }

    private Observable<List<MangaSync>> getSearchResultsObservable() {
        Observable<List<MangaSync>> observable;
        if (query.startsWith(PREFIX_MY)) {
            String realQuery = query.substring(PREFIX_MY.length()).toLowerCase().trim();
            observable = myAnimeList.getList()
                    .flatMap(Observable::from)
                    .filter(manga -> manga.title.toLowerCase().contains(realQuery))
                    .toList();
        } else {
            observable = myAnimeList.search(query);
        }
        return observable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void updateRemote() {
        add(myAnimeList.update(mangaSync)
                .flatMap(response -> db.insertMangaSync(mangaSync).asRxObservable())
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
        if (TextUtils.isEmpty(query) || query.equals(this.query))
            return;

        this.query = query;
        start(GET_SEARCH_RESULTS);
    }

    public void restartSearch() {
        this.query = null;
        stop(GET_SEARCH_RESULTS);
    }

    public void registerManga(MangaSync manga) {
        manga.manga_id = this.manga.id;
        add(myAnimeList.bind(manga)
                .flatMap(response -> {
                    if (response.isSuccessful()) {
                        return db.insertMangaSync(manga).asRxObservable();
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

    public void refresh() {
        if (mangaSync != null) {
            start(REFRESH);
        }
    }
}
