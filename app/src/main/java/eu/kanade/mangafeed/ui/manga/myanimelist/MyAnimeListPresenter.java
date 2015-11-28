package eu.kanade.mangafeed.ui.manga.myanimelist;

import android.os.Bundle;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.chaptersync.ChapterSyncManager;
import eu.kanade.mangafeed.data.chaptersync.MyAnimeList;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.ChapterSync;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.EventBusHook;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class MyAnimeListPresenter extends BasePresenter<MyAnimeListFragment> {

    @Inject DatabaseHelper db;
    @Inject ChapterSyncManager syncManager;

    private MyAnimeList myAnimeList;
    private Manga manga;
    private ChapterSync chapterSync;

    private String query;

    private Subscription updateSubscription;

    private static final int GET_CHAPTER_SYNC = 1;
    private static final int GET_SEARCH_RESULTS = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        myAnimeList = syncManager.getMyAnimeList();

        restartableLatestCache(GET_CHAPTER_SYNC,
                () -> db.getChapterSync(manga, myAnimeList).createObservable()
                        .flatMap(Observable::from)
                        .doOnNext(chapterSync -> this.chapterSync = chapterSync)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread()),
                MyAnimeListFragment::setChapterSync);

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

        chapterSync.last_chapter_read = chapterNumber;

        add(updateSubscription = myAnimeList.update(chapterSync)
                .flatMap(response -> db.insertChapterSync(chapterSync).createObservable())
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

    public void registerManga(ChapterSync selectedManga) {
        selectedManga.manga_id = manga.id;
        db.insertChapterSync(selectedManga).executeAsBlocking();
    }
}
