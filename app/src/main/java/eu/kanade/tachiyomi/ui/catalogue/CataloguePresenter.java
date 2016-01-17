package eu.kanade.tachiyomi.ui.catalogue;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;

import com.pushtorefresh.storio.sqlite.operations.put.PutResult;

import java.util.List;

import javax.inject.Inject;

import eu.kanade.tachiyomi.data.cache.CoverCache;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.source.SourceManager;
import eu.kanade.tachiyomi.data.source.base.Source;
import eu.kanade.tachiyomi.data.source.model.MangasPage;
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter;
import eu.kanade.tachiyomi.util.RxPager;
import icepick.State;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class CataloguePresenter extends BasePresenter<CatalogueFragment> {

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;
    @Inject CoverCache coverCache;
    @Inject PreferencesHelper prefs;

    private Source source;
    @State int sourceId;

    private String query;

    private int currentPage;
    private RxPager pager;
    private MangasPage lastMangasPage;

    private PublishSubject<List<Manga>> mangaDetailSubject;

    private static final int GET_MANGA_LIST = 1;
    private static final int GET_MANGA_DETAIL = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            onProcessRestart();
        }

        mangaDetailSubject = PublishSubject.create();

        restartableReplay(GET_MANGA_LIST,
                () -> pager.pages().concatMap(page -> getMangasPageObservable(page + 1)),
                (view, pair) -> view.onAddPage(pair.first, pair.second),
                (view, error) -> {
                    view.onAddPageError();
                    Timber.e(error.getMessage());
                });

        restartableLatestCache(GET_MANGA_DETAIL,
                () -> mangaDetailSubject
                        .observeOn(Schedulers.io())
                        .flatMap(Observable::from)
                        .filter(manga -> !manga.initialized)
                        .window(3)
                        .concatMap(pack -> pack.concatMap(this::getMangaDetails))
                        .onBackpressureBuffer()
                        .observeOn(AndroidSchedulers.mainThread()),
                CatalogueFragment::updateImage,
                (view, error) -> Timber.e(error.getMessage()));
    }

    private void onProcessRestart() {
        source = sourceManager.get(sourceId);
        stop(GET_MANGA_LIST);
        stop(GET_MANGA_DETAIL);
    }

    public void startRequesting(Source source) {
        this.source = source;
        sourceId = source.getId();
        restartRequest(null);
    }

    public void restartRequest(String query) {
        this.query = query;
        stop(GET_MANGA_LIST);
        currentPage = 1;
        pager = new RxPager();

        start(GET_MANGA_DETAIL);
        start(GET_MANGA_LIST);
    }

    public void requestNext() {
        if (hasNextPage())
            pager.requestNext(++currentPage);
    }

    private Observable<Pair<Integer, List<Manga>>> getMangasPageObservable(int page) {
        MangasPage nextMangasPage = new MangasPage(page);
        if (page != 1) {
            nextMangasPage.url = lastMangasPage.nextPageUrl;
        }

        Observable<MangasPage> obs = !TextUtils.isEmpty(query) ?
            source.searchMangasFromNetwork(nextMangasPage, query) :
            source.pullPopularMangasFromNetwork(nextMangasPage);

        return obs.subscribeOn(Schedulers.io())
                .doOnNext(mangasPage -> lastMangasPage = mangasPage)
                .flatMap(mangasPage -> Observable.from(mangasPage.mangas))
                .map(this::networkToLocalManga)
                .toList()
                .map(mangas -> Pair.create(page, mangas))
                .doOnNext(pair -> {
                    if (mangaDetailSubject != null)
                        mangaDetailSubject.onNext(pair.second);
                })
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Manga networkToLocalManga(Manga networkManga) {
        Manga localManga = db.getManga(networkManga.url, source.getId()).executeAsBlocking();
        if (localManga == null) {
            PutResult result = db.insertManga(networkManga).executeAsBlocking();
            networkManga.id = result.insertedId();
            localManga = networkManga;
        }
        return localManga;
    }

    private Observable<Manga> getMangaDetails(final Manga manga) {
        return source.pullMangaFromNetwork(manga.url)
                .subscribeOn(Schedulers.io())
                .flatMap(networkManga -> {
                    manga.copyFrom(networkManga);
                    db.insertManga(manga).executeAsBlocking();
                    return Observable.just(manga);
                })
                .onErrorResumeNext(error -> Observable.just(manga));
    }

    public Source getSource() {
        return source;
    }

    public boolean hasNextPage() {
        return lastMangasPage != null && lastMangasPage.nextPageUrl != null;
    }

    public boolean isValidSource(Source source) {
        if (!source.isLoginRequired() || source.isLogged())
            return true;

        return !(prefs.getSourceUsername(source).equals("")
                || prefs.getSourcePassword(source).equals(""));
    }

    public List<Source> getEnabledSources() {
        // TODO filter by enabled source
        return sourceManager.getSources();
    }

    public void addMangaToLibrary(Manga manga) {
        manga.favorite = true;
        db.insertManga(manga).executeAsBlocking();
    }
}
