package eu.kanade.mangafeed.presenter;

import android.os.Bundle;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.activity.CatalogueActivity;
import nucleus.presenter.RxPresenter;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.internal.util.SubscriptionList;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class CataloguePresenter extends RxPresenter<CatalogueActivity> {

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;

    private Source selectedSource;

    private String mSearchName;
    private boolean mSearchMode;
    private final int SEARCH_TIMEOUT = 1000;
    private int mCurrentPage = 1;

    private Subscription mMangaFetchSubscription;
    private Subscription mMangaSearchSubscription;
    private Subscription mSearchViewSubscription;
    private Subscription mMangaDetailFetchSubscription;
    private PublishSubject<Observable<String>> mSearchViewPublishSubject;
    private PublishSubject<Observable<List<Manga>>> mMangaDetailPublishSubject;
    private SubscriptionList mResultSubscriptions = new SubscriptionList();

    private final String CURRENT_PAGE = "CATALOGUE_CURRENT_PAGE";

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mCurrentPage = savedState.getInt(CURRENT_PAGE);
        }

        selectedSource = sourceManager.getSelectedSource();
        getMangasFromSource(mCurrentPage);
        initializeSearch();
        initializeMangaDetailsLoader();
    }

    @Override
    protected void onTakeView(CatalogueActivity view) {
        super.onTakeView(view);

        view.setScrollPage(mCurrentPage - 1);

        view.setToolbarTitle(selectedSource.getName());

        if (view.getAdapter().getCount() == 0)
            view.showProgressBar();
    }

    @Override
    protected void onSave(@NonNull Bundle state) {
        super.onSave(state);
        state.putInt(CURRENT_PAGE, mCurrentPage);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mResultSubscriptions.unsubscribe();
    }

    private void initializeSearch() {
        remove(mSearchViewSubscription);

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
        remove(mMangaDetailFetchSubscription);

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
                .compose(deliverReplay())
                .subscribe(this.split(CatalogueActivity::updateImage));

        add(mMangaDetailFetchSubscription);
    }

    public void getMangasFromSource(int page) {
        mMangaFetchSubscription = getMangasSubscriber(
                selectedSource.pullPopularMangasFromNetwork(page));

        mResultSubscriptions.add(mMangaFetchSubscription);
    }

    public void getMangasFromSearch(int page) {
        mMangaSearchSubscription = getMangasSubscriber(
                selectedSource.searchMangasFromNetwork(mSearchName, page));

        mResultSubscriptions.add(mMangaSearchSubscription);
    }

    private Subscription getMangasSubscriber(Observable<List<Manga>> mangas) {
        return mangas
                .subscribeOn(Schedulers.io())
                .flatMap(Observable::from)
                .map(this::networkToLocalManga)
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .compose(deliverReplay())
                .subscribe(this.split((view, newMangas) -> {
                    view.hideProgressBar();
                    view.onMangasNext(newMangas);
                    if (mMangaDetailPublishSubject != null)
                        mMangaDetailPublishSubject.onNext(Observable.just(newMangas));
                }));
    }

    private Manga networkToLocalManga(Manga networkManga) {
        Manga localManga = db.getMangaBlock(networkManga.url);
        if (localManga == null) {
            db.insertMangaBlock(networkManga);
            localManga = db.getMangaBlock(networkManga.url);
        }
        return localManga;
    }

    public void onQueryTextChange(String query) {
        if (mSearchViewPublishSubject != null)
            mSearchViewPublishSubject.onNext(Observable.just(query));
    }

    private void queryFromSearch(String query) {
        // If search button clicked
        if (mSearchName.equals("") && query.equals("")) {
            return;
        }
        // If going to search mode
        else if (mSearchName.equals("") && !query.equals("")) {
            mSearchMode = true;
            mResultSubscriptions.clear();
        }
        // If going to normal mode
        else if (!mSearchName.equals("") && query.equals("")) {
            mSearchMode = false;
            mResultSubscriptions.clear();
        }

        mSearchName = query;
        getView().getAdapter().getItems().clear();
        getView().showProgressBar();
        getView().resetScrollListener();
        loadMoreMangas(1);
    }

    public void loadMoreMangas(int page) {
        if (page > 1) {
            getView().showGridProgressBar();
        }
        if (mSearchMode) {
            getMangasFromSearch(page);
        } else {
            getMangasFromSource(page);
        }
        mCurrentPage = page;
    }

}
