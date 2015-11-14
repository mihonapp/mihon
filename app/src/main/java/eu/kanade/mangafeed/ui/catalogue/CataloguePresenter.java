package eu.kanade.mangafeed.ui.catalogue;

import android.os.Bundle;

import com.pushtorefresh.storio.sqlite.operations.put.PutResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.PageBundle;
import eu.kanade.mangafeed.util.RxPager;
import icepick.State;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class CataloguePresenter extends BasePresenter<CatalogueFragment> {

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;

    private Source selectedSource;

    @State protected String mSearchName;
    @State protected boolean mSearchMode;
    private final int SEARCH_TIMEOUT = 1000;

    private int mCurrentPage;
    private RxPager pager;

    private Subscription mQueryDebouncerSubscription;
    private Subscription mMangaDetailFetchSubscription;
    private PublishSubject<String> mQueryDebouncerSubject;
    private PublishSubject<List<Manga>> mMangaDetailPublishSubject;

    private static final int GET_MANGA_LIST = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableReplay(GET_MANGA_LIST,
                () -> pager.pages().<PageBundle<List<Manga>>>concatMap(
                        page -> getMangaObs(page + 1)
                                .map(mangas -> new PageBundle<>(page, mangas))
                                .observeOn(AndroidSchedulers.mainThread())
                ),
                (view, page) -> {
                    view.hideProgressBar();
                    view.onAddPage(page);
                    if (mMangaDetailPublishSubject != null)
                        mMangaDetailPublishSubject.onNext(page.data);
                });

        initializeSearch();
        initializeMangaDetailsLoader();
    }

    @Override
    protected void onTakeView(CatalogueFragment view) {
        super.onTakeView(view);

        view.setToolbarTitle(selectedSource.getName());

        if (mSearchMode)
            view.restoreSearch(mSearchName);
    }

    public void startRequesting(int sourceId) {
        selectedSource = sourceManager.get(sourceId);
        restartRequest();
    }

    private void restartRequest() {
        stop(GET_MANGA_LIST);
        mCurrentPage = 1;
        pager = new RxPager();
        if (getView() != null)
            getView().showProgressBar();

        start(GET_MANGA_LIST);
    }

    public void requestNext() {
        if (getView() != null)
            getView().showGridProgressBar();

        pager.requestNext(++mCurrentPage);
    }

    private Observable<List<Manga>> getMangaObs(int page) {
        Observable<List<Manga>> obs;
        if (mSearchMode)
            obs = selectedSource.searchMangasFromNetwork(mSearchName, page);
        else
            obs = selectedSource.pullPopularMangasFromNetwork(page);

        return obs.subscribeOn(Schedulers.io())
                .flatMap(Observable::from)
                .map(this::networkToLocalManga)
                .toList();
    }

    private Manga networkToLocalManga(Manga networkManga) {
        List<Manga> dbResult = db.getManga(networkManga.url).executeAsBlocking();
        Manga localManga = !dbResult.isEmpty() ? dbResult.get(0) : null;
        if (localManga == null) {
            PutResult result = db.insertManga(networkManga).executeAsBlocking();
            networkManga.id = result.insertedId();
            localManga = networkManga;
        }
        return localManga;
    }

    private void initializeSearch() {
        if (mQueryDebouncerSubscription != null)
            return;

        mSearchName = "";
        mSearchMode = false;
        mQueryDebouncerSubject = PublishSubject.create();

        add(mQueryDebouncerSubscription = mQueryDebouncerSubject
                .debounce(SEARCH_TIMEOUT, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::queryFromSearch));
    }

    private void initializeMangaDetailsLoader() {
        if (mMangaDetailFetchSubscription != null)
            return;

        mMangaDetailPublishSubject = PublishSubject.create();

        add(mMangaDetailFetchSubscription = mMangaDetailPublishSubject
                .subscribeOn(Schedulers.io())
                .flatMap(Observable::from)
                .filter(manga -> !manga.initialized)
                .window(3)
                .concatMap(pack -> pack.concatMap(this::getMangaDetails))
                .filter(manga -> manga.initialized)
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(manga -> {
                    if (getView() != null)
                        getView().updateImage(manga);
                }));
    }

    private Observable<Manga> getMangaDetails(final Manga manga) {
        return selectedSource.pullMangaFromNetwork(manga.url)
                .subscribeOn(Schedulers.io())
                .flatMap(networkManga -> {
                    Manga.copyFromNetwork(manga, networkManga);
                    db.insertManga(manga).executeAsBlocking();
                    return Observable.just(manga);
                });
    }

    public void onSearchEvent(String query, boolean now) {
        // If the query is empty or not debounced, resolve it instantly
        if (now || query.equals(""))
            queryFromSearch(query);
        else if (mQueryDebouncerSubject != null)
            mQueryDebouncerSubject.onNext(query);
    }

    private void queryFromSearch(String query) {
        // If text didn't change, do nothing
        if (mSearchName.equals(query)) {
            return;
        }
        // If going to search mode
        else if (mSearchName.equals("") && !query.equals("")) {
            mSearchMode = true;
        }
        // If going to normal mode
        else if (!mSearchName.equals("") && query.equals("")) {
            mSearchMode = false;
        }

        mSearchName = query;
        restartRequest();
    }

}
