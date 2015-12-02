package eu.kanade.mangafeed.ui.catalogue;

import android.os.Bundle;

import com.pushtorefresh.storio.sqlite.operations.put.PutResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.source.model.MangasPage;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.PageBundle;
import eu.kanade.mangafeed.util.RxPager;
import icepick.State;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class CataloguePresenter extends BasePresenter<CatalogueFragment> {

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;

    private Source selectedSource;

    @State protected String searchName;
    @State protected boolean searchMode;
    private final int SEARCH_TIMEOUT = 1000;

    private int currentPage;
    private RxPager pager;
    private MangasPage lastMangasPage;

    private Subscription queryDebouncerSubscription;
    private PublishSubject<String> queryDebouncerSubject;
    private PublishSubject<List<Manga>> mangaDetailSubject;

    private static final int GET_MANGA_LIST = 1;
    private static final int GET_MANGA_DETAIL = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mangaDetailSubject = PublishSubject.create();

        restartableReplay(GET_MANGA_LIST,
                () -> pager.pages().<PageBundle<List<Manga>>>concatMap(
                        page -> getMangaObs(page + 1)
                                .map(mangas -> new PageBundle<>(page, mangas))
                                .observeOn(AndroidSchedulers.mainThread())),
                (view, page) -> {
                    view.onAddPage(page);
                    if (mangaDetailSubject != null)
                        mangaDetailSubject.onNext(page.data);
                },
                (view, error) -> Timber.e(error.fillInStackTrace(), error.getMessage()));

        restartableLatestCache(GET_MANGA_DETAIL,
                () -> mangaDetailSubject
                        .subscribeOn(Schedulers.io())
                        .flatMap(Observable::from)
                        .filter(manga -> !manga.initialized)
                        .window(3)
                        .concatMap(pack -> pack.concatMap(this::getMangaDetails))
                        .filter(manga -> manga.initialized)
                        .onBackpressureBuffer()
                        .observeOn(AndroidSchedulers.mainThread()),
                (view, manga) -> {
                    view.updateImage(manga);
                },
                (view, error) -> Timber.e(error.fillInStackTrace(), error.getMessage()));

        initializeSearch();
    }

    @Override
    protected void onTakeView(CatalogueFragment view) {
        super.onTakeView(view);

        view.setToolbarTitle(selectedSource.getName());

        if (searchMode)
            view.restoreSearch(searchName);
    }

    public void startRequesting(int sourceId) {
        selectedSource = sourceManager.get(sourceId);
        restartRequest();
    }

    private void restartRequest() {
        stop(GET_MANGA_LIST);
        currentPage = 1;
        pager = new RxPager();
        if (getView() != null)
            getView().showProgressBar();

        start(GET_MANGA_DETAIL);
        start(GET_MANGA_LIST);
    }

    public boolean requestNext() {
        if (lastMangasPage.nextPageUrl == null)
            return false;

        pager.requestNext(++currentPage);
        return true;
    }

    private Observable<List<Manga>> getMangaObs(int page) {
        MangasPage nextMangasPage = new MangasPage(page);
        if (page != 1) {
            nextMangasPage.url = lastMangasPage.nextPageUrl;
        }

        Observable<MangasPage> obs;
        if (searchMode)
            obs = selectedSource.searchMangasFromNetwork(nextMangasPage, searchName);
        else
            obs = selectedSource.pullPopularMangasFromNetwork(nextMangasPage);

        return obs.subscribeOn(Schedulers.io())
                .doOnNext(mangasPage -> lastMangasPage = mangasPage)
                .flatMap(mangasPage -> Observable.from(mangasPage.mangas))
                .map(this::networkToLocalManga)
                .toList();
    }

    private Manga networkToLocalManga(Manga networkManga) {
        List<Manga> dbResult = db.getManga(networkManga.url, selectedSource.getSourceId()).executeAsBlocking();
        Manga localManga = !dbResult.isEmpty() ? dbResult.get(0) : null;
        if (localManga == null) {
            PutResult result = db.insertManga(networkManga).executeAsBlocking();
            networkManga.id = result.insertedId();
            localManga = networkManga;
        }
        return localManga;
    }

    private void initializeSearch() {
        if (queryDebouncerSubscription != null)
            return;

        searchName = "";
        searchMode = false;
        queryDebouncerSubject = PublishSubject.create();

        add(queryDebouncerSubscription = queryDebouncerSubject
                .debounce(SEARCH_TIMEOUT, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::queryFromSearch));
    }

    private Observable<Manga> getMangaDetails(final Manga manga) {
        return selectedSource.pullMangaFromNetwork(manga.url)
                .subscribeOn(Schedulers.io())
                .flatMap(networkManga -> {
                    Manga.copyFromNetwork(manga, networkManga);
                    db.insertManga(manga).executeAsBlocking();
                    return Observable.just(manga);
                })
                .onErrorResumeNext(error -> {
                    return Observable.just(manga);
                });
    }

    public void onSearchEvent(String query, boolean now) {
        // If the query is empty or not debounced, resolve it instantly
        if (now || query.equals(""))
            queryFromSearch(query);
        else if (queryDebouncerSubject != null)
            queryDebouncerSubject.onNext(query);
    }

    private void queryFromSearch(String query) {
        // If text didn't change, do nothing
        if (searchName.equals(query)) {
            return;
        }
        // If going to search mode
        else if (searchName.equals("") && !query.equals("")) {
            searchMode = true;
        }
        // If going to normal mode
        else if (!searchName.equals("") && query.equals("")) {
            searchMode = false;
        }

        searchName = query;
        restartRequest();
    }

    public Source getSource() {
        return selectedSource;
    }

}
