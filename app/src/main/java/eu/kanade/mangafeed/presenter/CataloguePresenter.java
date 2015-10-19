package eu.kanade.mangafeed.presenter;

import android.os.Bundle;

import com.pushtorefresh.storio.sqlite.operations.put.PutResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.activity.CatalogueActivity;
import eu.kanade.mangafeed.util.PageBundle;
import eu.kanade.mangafeed.util.RxPager;
import icepick.State;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class CataloguePresenter extends BasePresenter<CatalogueActivity> {

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;

    private Source selectedSource;

    @State protected String mSearchName;
    @State protected boolean mSearchMode;
    private final int SEARCH_TIMEOUT = 1000;

    @State protected int mCurrentPage;
    private RxPager pager;

    private Subscription mSearchViewSubscription;
    private Subscription mMangaDetailFetchSubscription;
    private PublishSubject<Observable<String>> mSearchViewPublishSubject;
    private PublishSubject<Observable<List<Manga>>> mMangaDetailPublishSubject;

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
                        mMangaDetailPublishSubject.onNext(Observable.just(page.data));
                });

        initializeSearch();
        initializeMangaDetailsLoader();
    }

    @Override
    protected void onTakeView(CatalogueActivity view) {
        super.onTakeView(view);

        view.setToolbarTitle(selectedSource.getName());

        if (mSearchMode)
            view.restoreSearch(mSearchName);
    }

    public void requestNext() {
        pager.requestNext(++mCurrentPage);
        if (getView() != null)
            getView().showGridProgressBar();
    }

    public void startRequesting(int source_id) {
        this.selectedSource = sourceManager.get(source_id);
        restartRequest();
    }

    private void restartRequest() {
        stop(GET_MANGA_LIST);
        mCurrentPage = 1;
        pager = new RxPager();
        start(GET_MANGA_LIST);
        if (getView() != null)
            getView().showProgressBar();
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

    private void initializeSearch() {
        if (mSearchViewSubscription != null)
            return;

        mSearchName = "";
        mSearchMode = false;
        mSearchViewPublishSubject = PublishSubject.create();

        mSearchViewSubscription = Observable.switchOnNext(mSearchViewPublishSubject)
                .debounce(SEARCH_TIMEOUT, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::queryFromSearch,
                        error -> Timber.e(error.getCause(), error.getMessage()));

        add(mSearchViewSubscription);
    }

    private void initializeMangaDetailsLoader() {
        if (mMangaDetailFetchSubscription != null)
            return;

        mMangaDetailPublishSubject = PublishSubject.create();

        mMangaDetailFetchSubscription = Observable.switchOnNext(mMangaDetailPublishSubject)
                .subscribeOn(Schedulers.io())
                .flatMap(Observable::from)
                .filter(manga -> !manga.initialized)
                .buffer(3)
                .concatMap(localMangas -> {
                    List<Observable<Manga>> mangaObservables = new ArrayList<>();
                    for (Manga manga : localMangas) {
                        Observable<Manga> tempObs = selectedSource.pullMangaFromNetwork(manga.url)
                                .subscribeOn(Schedulers.io())
                                .flatMap(networkManga -> {
                                    Manga.copyFromNetwork(manga, networkManga);
                                    db.insertMangaBlock(manga);
                                    return Observable.just(manga);
                                });
                        mangaObservables.add(tempObs);
                    }
                    return Observable.merge(mangaObservables);
                })
                .filter(manga -> manga.initialized)
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(manga -> {
                    if (getView() != null)
                        getView().updateImage(manga);
                });

        add(mMangaDetailFetchSubscription);
    }

    private Manga networkToLocalManga(Manga networkManga) {
        Manga localManga = db.getMangaBlock(networkManga.url);
        if (localManga == null) {
            PutResult result = db.insertMangaBlock(networkManga);
            networkManga.id = result.insertedId();
            localManga = networkManga;
        }
        return localManga;
    }

    public void onQueryTextChange(String query) {
        if (mSearchViewPublishSubject != null)
            mSearchViewPublishSubject.onNext(Observable.just(query));
    }

    private void queryFromSearch(String query) {
        // If text didn't change
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
