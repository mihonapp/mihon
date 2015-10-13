package eu.kanade.mangafeed.presenter;

import android.content.Intent;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
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
    private Subscription mMangaDetailFetchSubscription;
    private PublishSubject<Observable<String>> mSearchViewPublishSubject;
    private PublishSubject<Observable<List<Manga>>> mMangaDetailPublishSubject;


    public CatalogueListPresenter(CatalogueListView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initialize() {
        initializeSource();
        initializeAdapter();
        initializeSearch();
        initializeMangaDetailsLoader();

        getMangasFromSource(1);
    }

    private void initializeSource() {
        int sourceId = view.getIntent().getIntExtra(Intent.EXTRA_UID, -1);
        selectedSource = sourceManager.get(sourceId);
        view.setSourceTitle(selectedSource.getName());
    }

    private void initializeAdapter() {
        adapter = new EasyAdapter<>(view.getActivity(), CatalogueListHolder.class);
        view.setAdapter(adapter);
        view.setScrollListener();
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

    private void initializeMangaDetailsLoader() {
        mMangaDetailPublishSubject = PublishSubject.create();

        mMangaDetailFetchSubscription = Observable.switchOnNext(mMangaDetailPublishSubject)
                .subscribeOn(Schedulers.io())
                .flatMap(Observable::from)
                .filter(manga -> !manga.initialized)
                .buffer(5)
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
                    // Get manga index in the adapter
                    int index = getMangaIndex(manga);
                    // Get the image view associated with the manga.
                    // If it's null (not visible in the screen) there's no need to update the image.
                    ImageView imageView = view.getImageView(index);
                    if (imageView != null) {
                        updateImage(imageView, manga.thumbnail_url);
                    }
                });

        subscriptions.add(mMangaDetailFetchSubscription);
    }

    public void getMangasFromSource(int page) {
        subscriptions.remove(mMangaFetchSubscription);

        mMangaFetchSubscription = selectedSource.pullPopularMangasFromNetwork(page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .map(this::networkToLocalManga)
                .toList()
                .subscribe(newMangas -> {
                    adapter.addItems(newMangas);
                    mMangaDetailPublishSubject.onNext(Observable.just(newMangas));
                });

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
                .subscribe(newMangas -> {
                    adapter.addItems(newMangas);
                    mMangaDetailPublishSubject.onNext(Observable.just(newMangas));
                });

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


    private void queryFromSearch(String query) {
        // If search button clicked
        if (mSearchName.equals("") && query.equals("")) {
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
        adapter.getItems().clear();
        view.resetScrollListener();
        loadMoreMangas(1);
    }

    public void loadMoreMangas(int page) {
        if (mSearchMode) {
            getMangasFromSearch(page);
        } else {
            getMangasFromSource(page);
        }
    }

    private int getMangaIndex(Manga manga) {
        int i;
        for (i = 0; i < adapter.getCount(); i++) {
            if (manga.id == adapter.getItem(i).id) {
                return i;
            }
        }
        return -1;
    }

    private void updateImage(ImageView imageView, String thumbnail) {
        Glide.with(view.getActivity())
                .load(thumbnail)
                .centerCrop()
                .into(imageView);
    }

}
