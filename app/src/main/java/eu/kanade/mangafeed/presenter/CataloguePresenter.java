package eu.kanade.mangafeed.presenter;

import android.content.Intent;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.activity.MangaCatalogueActivity;
import eu.kanade.mangafeed.ui.adapter.CatalogueHolder;
import eu.kanade.mangafeed.view.CatalogueView;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;
import uk.co.ribot.easyadapter.EasyAdapter;

public class CataloguePresenter extends BasePresenter {

    CatalogueView view;
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


    public CataloguePresenter(CatalogueView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initialize() {
        initializeSource();
        initializeAdapter();
        initializeSearch();
        initializeMangaDetailsLoader();

        view.showProgressBar();
        getMangasFromSource(1);
    }

    private void initializeSource() {
        int sourceId = view.getIntent().getIntExtra(Intent.EXTRA_UID, -1);
        selectedSource = sourceManager.get(sourceId);
        view.setTitle(selectedSource.getName());
    }

    private void initializeAdapter() {
        adapter = new EasyAdapter<>(view.getActivity(), CatalogueHolder.class);
        view.setAdapter(adapter);
        view.setScrollListener();
        view.setMangaClickListener();
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

        mMangaFetchSubscription = getMangasSubscriber(
                selectedSource.pullPopularMangasFromNetwork(page));

        subscriptions.add(mMangaFetchSubscription);
    }

    public void getMangasFromSearch(int page) {
        subscriptions.remove(mMangaSearchSubscription);

        mMangaSearchSubscription = getMangasSubscriber(
                selectedSource.searchMangasFromNetwork(mSearchName, page));

        subscriptions.add(mMangaSearchSubscription);
    }

    private Subscription getMangasSubscriber(Observable<List<Manga>> mangas) {
        return mangas
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .map(this::networkToLocalManga)
                .toList()
                .subscribe(newMangas -> {
                    view.hideProgressBar();
                    adapter.addItems(newMangas);
                    if (mMangaDetailPublishSubject != null)
                        mMangaDetailPublishSubject.onNext(Observable.just(newMangas));
                });
    }

    private Manga networkToLocalManga(Manga networkManga) {
        Manga localManga = db.getMangaBlock(networkManga.url);
        if (localManga == null) {
            db.insertMangaBlock(networkManga);
            localManga = db.getMangaBlock(networkManga.url);
        }
        return localManga;
    }

    public void onMangaClick(int position) {
        Intent intent = new Intent(view.getActivity(), MangaCatalogueActivity.class);
        Manga selectedManga = adapter.getItem(position);
        EventBus.getDefault().postSticky(selectedManga);
        view.getActivity().startActivity(intent);
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
        view.showProgressBar();
        view.resetScrollListener();
        loadMoreMangas(1);
    }

    public void loadMoreMangas(int page) {
        if (page > 1) {
            view.showGridProgressBar();
        }
        if (mSearchMode) {
            getMangasFromSearch(page);
        } else {
            getMangasFromSource(page);
        }
    }

    private int getMangaIndex(Manga manga) {
        for (int i = 0; i < adapter.getCount(); i++) {
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
