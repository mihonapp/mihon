package eu.kanade.mangafeed.presenter;

import android.content.Intent;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.adapter.CatalogueListHolder;
import eu.kanade.mangafeed.view.CatalogueListView;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;
import uk.co.ribot.easyadapter.EasyAdapter;

public class CatalogueListPresenter extends BasePresenter {

    CatalogueListView view;
    EasyAdapter<Manga> adapter;
    Source selectedSource;

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;

    private String mSearchName;
    private boolean mSearchMode;
    private final int SEARCH_TIMEOUT = 1000;

    private Subscription mMangaFetchSubscription;
    private Subscription mMangaSearchSubscription;
    private Subscription mSearchViewSubscription;
    private PublishSubject<Observable<String>> mSearchViewPublishSubject;


    public CatalogueListPresenter(CatalogueListView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initialize() {
        int sourceId = view.getIntent().getIntExtra(Intent.EXTRA_UID, -1);
        selectedSource = sourceManager.get(sourceId);
        view.setSourceTitle(selectedSource.getName());

        adapter = new EasyAdapter<>(view.getActivity(), CatalogueListHolder.class);
        view.setAdapter(adapter);
        view.setScrollListener();

        initializeSearch();

        getMangasFromSource(1);
    }

    public void getMangasFromSource(int page) {
        subscriptions.remove(mMangaFetchSubscription);

        mMangaFetchSubscription = selectedSource.pullPopularMangasFromNetwork(page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .map(this::networkToLocalManga)
                .toList()
                .subscribe(adapter::addItems);

        subscriptions.add(mMangaFetchSubscription);
    }

    public void getMangasFromSearch(int page) {
        subscriptions.remove(mMangaSearchSubscription);

        mMangaSearchSubscription = selectedSource.searchMangasFromNetwork(mSearchName, page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .map(this::networkToLocalManga)
                .toList()
                .subscribe(adapter::addItems);

        subscriptions.add(mMangaSearchSubscription);
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

    private void initializeSearch() {
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

        subscriptions.add(mSearchViewSubscription);
    }

    private void queryFromSearch(String query) {
        // If search button clicked
        if (mSearchName.equals("") && query.equals("")) {
            return;
        }
        // If going to search mode
        else if (mSearchName.equals("") && !query.equals("")) {
            mSearchMode = true;
            mSearchName = query;
            adapter.setItems(new ArrayList<>());
            getMangasFromSearch(1);
        }
        // If going to normal mode
        else if (!mSearchName.equals("") && query.equals("")) {
            mSearchMode = false;
            mSearchName = query;
            adapter.setItems(new ArrayList<>());
            getMangasFromSource(1);
        }
        // If query changes
        else {
            mSearchName = query;
            adapter.setItems(new ArrayList<>());
            getMangasFromSearch(1);
        }
        view.setScrollListener();
    }

    public void loadMoreMangas(int page) {
        if (!mSearchMode) {
            getMangasFromSource(page);
        } else {
            getMangasFromSearch(page);
        }
    }

}
