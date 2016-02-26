package eu.kanade.tachiyomi.data.source.base;

import android.content.Context;

import com.bumptech.glide.load.model.LazyHeaders;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import eu.kanade.tachiyomi.App;
import eu.kanade.tachiyomi.data.cache.ChapterCache;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.network.NetworkHelper;
import eu.kanade.tachiyomi.data.network.ReqKt;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.source.model.MangasPage;
import eu.kanade.tachiyomi.data.source.model.Page;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;
import rx.schedulers.Schedulers;

public abstract class Source extends BaseSource {

    @Inject protected NetworkHelper networkService;
    @Inject protected ChapterCache chapterCache;
    @Inject protected PreferencesHelper prefs;
    protected Headers requestHeaders;
    protected LazyHeaders glideHeaders;

    public Source() {}

    public Source(Context context) {
        App.get(context).getComponent().inject(this);
        requestHeaders = headersBuilder().build();
        glideHeaders = glideHeadersBuilder().build();
    }

    @Override
    public boolean isLoginRequired() {
        return false;
    }

    protected Request popularMangaRequest(MangasPage page) {
        if (page.page == 1) {
            page.url = getInitialPopularMangasUrl();
        }

        return ReqKt.get(page.url, requestHeaders);
    }

    protected Request searchMangaRequest(MangasPage page, String query) {
        if (page.page == 1) {
            page.url = getInitialSearchUrl(query);
        }

        return ReqKt.get(page.url, requestHeaders);
    }

    protected Request mangaDetailsRequest(String mangaUrl) {
        return ReqKt.get(getBaseUrl() + mangaUrl, requestHeaders);
    }

    protected Request chapterListRequest(String mangaUrl) {
        return ReqKt.get(getBaseUrl() + mangaUrl, requestHeaders);
    }

    protected Request pageListRequest(String chapterUrl) {
        return ReqKt.get(getBaseUrl() + chapterUrl, requestHeaders);
    }

    protected Request imageUrlRequest(Page page) {
        return ReqKt.get(page.getUrl(), requestHeaders);
    }

    protected Request imageRequest(Page page) {
        return ReqKt.get(page.getImageUrl(), requestHeaders);
    }

    // Get the most popular mangas from the source
    public Observable<MangasPage> pullPopularMangasFromNetwork(MangasPage page) {
        return networkService
                .requestBody(popularMangaRequest(page), true)
                .map(Jsoup::parse)
                .doOnNext(doc -> page.mangas = parsePopularMangasFromHtml(doc))
                .doOnNext(doc -> page.nextPageUrl = parseNextPopularMangasUrl(doc, page))
                .map(response -> page);
    }

    // Get mangas from the source with a query
    public Observable<MangasPage> searchMangasFromNetwork(MangasPage page, String query) {
        return networkService
                .requestBody(searchMangaRequest(page, query), true)
                .map(Jsoup::parse)
                .doOnNext(doc -> page.mangas = parseSearchFromHtml(doc))
                .doOnNext(doc -> page.nextPageUrl = parseNextSearchUrl(doc, page, query))
                .map(response -> page);
    }

    // Get manga details from the source
    public Observable<Manga> pullMangaFromNetwork(final String mangaUrl) {
        return networkService
                .requestBody(mangaDetailsRequest(mangaUrl))
                .flatMap(unparsedHtml -> Observable.just(parseHtmlToManga(mangaUrl, unparsedHtml)));
    }

    // Get chapter list of a manga from the source
    public Observable<List<Chapter>> pullChaptersFromNetwork(final String mangaUrl) {
        return networkService
                .requestBody(chapterListRequest(mangaUrl))
                .flatMap(unparsedHtml -> {
                    List<Chapter> chapters = parseHtmlToChapters(unparsedHtml);
                    return !chapters.isEmpty() ?
                            Observable.just(chapters) :
                            Observable.error(new Exception("No chapters found"));
                });
    }

    public Observable<List<Page>> getCachedPageListOrPullFromNetwork(final String chapterUrl) {
        return chapterCache.getPageListFromCache(getChapterCacheKey(chapterUrl))
                .onErrorResumeNext(throwable -> {
                    return pullPageListFromNetwork(chapterUrl);
                })
                .onBackpressureBuffer();
    }

    public Observable<List<Page>> pullPageListFromNetwork(final String chapterUrl) {
        return networkService
                .requestBody(pageListRequest(chapterUrl))
                .flatMap(unparsedHtml -> {
                    List<Page> pages = convertToPages(parseHtmlToPageUrls(unparsedHtml));
                    return !pages.isEmpty() ?
                            Observable.just(parseFirstPage(pages, unparsedHtml)) :
                            Observable.error(new Exception("Page list is empty"));
                });
    }

    public Observable<Page> getAllImageUrlsFromPageList(final List<Page> pages) {
        return Observable.from(pages)
                .filter(page -> page.getImageUrl() != null)
                .mergeWith(getRemainingImageUrlsFromPageList(pages));
    }

    // Get the URLs of the images of a chapter
    public Observable<Page> getRemainingImageUrlsFromPageList(final List<Page> pages) {
        return Observable.from(pages)
                .filter(page -> page.getImageUrl() == null)
                .concatMap(this::getImageUrlFromPage);
    }

    public Observable<Page> getImageUrlFromPage(final Page page) {
        page.setStatus(Page.LOAD_PAGE);
        return networkService
                .requestBody(imageUrlRequest(page))
                .flatMap(unparsedHtml -> Observable.just(parseHtmlToImageUrl(unparsedHtml)))
                .onErrorResumeNext(e -> {
                    page.setStatus(Page.ERROR);
                    return Observable.just(null);
                })
                .flatMap(imageUrl -> {
                    page.setImageUrl(imageUrl);
                    return Observable.just(page);
                })
                .subscribeOn(Schedulers.io());
    }

    public Observable<Page> getCachedImage(final Page page) {
        Observable<Page> pageObservable = Observable.just(page);
        if (page.getImageUrl() == null)
            return pageObservable;

        return pageObservable
                .flatMap(p -> {
                    if (!chapterCache.isImageInCache(page.getImageUrl())) {
                        return cacheImage(page);
                    }
                    return Observable.just(page);
                })
                .flatMap(p -> {
                    page.setImagePath(chapterCache.getImagePath(page.getImageUrl()));
                    page.setStatus(Page.READY);
                    return Observable.just(page);
                })
                .onErrorResumeNext(e -> {
                    page.setStatus(Page.ERROR);
                    return Observable.just(page);
                });
    }

    private Observable<Page> cacheImage(final Page page) {
        page.setStatus(Page.DOWNLOAD_IMAGE);
        return getImageProgressResponse(page)
                .flatMap(resp -> {
                    try {
                        chapterCache.putImageToCache(page.getImageUrl(), resp);
                    } catch (IOException e) {
                        return Observable.error(e);
                    }
                    return Observable.just(page);
                });
    }

    public Observable<Response> getImageProgressResponse(final Page page) {
        return networkService.requestBodyProgress(imageRequest(page), page);
    }

    public void savePageList(String chapterUrl, List<Page> pages) {
        if (pages != null)
            chapterCache.putPageListToCache(getChapterCacheKey(chapterUrl), pages);
    }

    protected List<Page> convertToPages(List<String> pageUrls) {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < pageUrls.size(); i++) {
            pages.add(new Page(i, pageUrls.get(i)));
        }
        return pages;
    }

    protected List<Page> parseFirstPage(List<Page> pages, String unparsedHtml) {
        String firstImage = parseHtmlToImageUrl(unparsedHtml);
        pages.get(0).setImageUrl(firstImage);
        return pages;
    }

    protected String getChapterCacheKey(String chapterUrl) {
        return getId() + chapterUrl;
    }

    // Overridable method to allow custom parsing.
    public void parseChapterNumber(Chapter chapter) {

    }

    protected LazyHeaders.Builder glideHeadersBuilder() {
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        for (Map.Entry<String, List<String>> entry : requestHeaders.toMultimap().entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue().get(0));
        }

        return builder;
    }

    public LazyHeaders getGlideHeaders() {
        return glideHeaders;
    }

}
