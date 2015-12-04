package eu.kanade.mangafeed.data.source.base;

import android.content.Context;

import com.bumptech.glide.load.model.LazyHeaders;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.cache.CacheManager;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.network.NetworkHelper;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.model.MangasPage;
import eu.kanade.mangafeed.data.source.model.Page;
import rx.Observable;
import rx.schedulers.Schedulers;

public abstract class Source extends BaseSource {

    @Inject protected NetworkHelper networkService;
    @Inject protected CacheManager cacheManager;
    @Inject protected PreferencesHelper prefs;
    protected Headers requestHeaders;
    protected LazyHeaders glideHeaders;

    public Source(Context context) {
        App.get(context).getComponent().inject(this);
        requestHeaders = headersBuilder().build();
        glideHeaders = glideHeadersBuilder().build();
    }

    // Get the most popular mangas from the source
    public Observable<MangasPage> pullPopularMangasFromNetwork(MangasPage page) {
        if (page.page == 1)
            page.url = getInitialPopularMangasUrl();

        return networkService
                .getStringResponse(page.url, requestHeaders, null)
                .map(Jsoup::parse)
                .doOnNext(doc -> page.mangas = parsePopularMangasFromHtml(doc))
                .doOnNext(doc -> page.nextPageUrl = parseNextPopularMangasUrl(doc, page))
                .map(response -> page);
    }

    // Get mangas from the source with a query
    public Observable<MangasPage> searchMangasFromNetwork(MangasPage page, String query) {
        if (page.page == 1)
            page.url = getInitialSearchUrl(query);

        return networkService
                .getStringResponse(page.url, requestHeaders, null)
                .map(Jsoup::parse)
                .doOnNext(doc -> page.mangas = parseSearchFromHtml(doc))
                .doOnNext(doc -> page.nextPageUrl = parseNextSearchUrl(doc, page, query))
                .map(response -> page);
    }

    // Get manga details from the source
    public Observable<Manga> pullMangaFromNetwork(final String mangaUrl) {
        return networkService
                .getStringResponse(getBaseUrl() + overrideMangaUrl(mangaUrl), requestHeaders, null)
                .flatMap(unparsedHtml -> Observable.just(parseHtmlToManga(mangaUrl, unparsedHtml)));
    }

    // Get chapter list of a manga from the source
    public Observable<List<Chapter>> pullChaptersFromNetwork(final String mangaUrl) {
        return networkService
                .getStringResponse(getBaseUrl() + mangaUrl, requestHeaders, null)
                .flatMap(unparsedHtml ->
                        Observable.just(parseHtmlToChapters(unparsedHtml)));
    }

    public Observable<List<Page>> getCachedPageListOrPullFromNetwork(final String chapterUrl) {
        return cacheManager.getPageUrlsFromDiskCache(getChapterCacheKey(chapterUrl))
                .onErrorResumeNext(throwable -> {
                    return pullPageListFromNetwork(chapterUrl);
                })
                .onBackpressureBuffer();
    }

    public Observable<List<Page>> pullPageListFromNetwork(final String chapterUrl) {
        return networkService
                .getStringResponse(getBaseUrl() + overrideChapterUrl(chapterUrl), requestHeaders, null)
                .flatMap(unparsedHtml -> {
                    List<String> pageUrls = parseHtmlToPageUrls(unparsedHtml);
                    return Observable.just(getFirstImageFromPageUrls(pageUrls, unparsedHtml));
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
                .getStringResponse(overridePageUrl(page.getUrl()), requestHeaders, null)
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
                    if (!cacheManager.isImageInCache(page.getImageUrl())) {
                        return cacheImage(page);
                    }
                    return Observable.just(page);
                })
                .flatMap(p -> {
                    page.setImagePath(cacheManager.getImagePath(page.getImageUrl()));
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
                        cacheManager.putImageToDiskCache(page.getImageUrl(), resp);
                    } catch (IOException e) {
                        return Observable.error(e);
                    }
                    return Observable.just(page);
                });
    }

    public Observable<Response> getImageProgressResponse(final Page page) {
        return networkService.getProgressResponse(page.getImageUrl(), requestHeaders, page);
    }

    public void savePageList(String chapterUrl, List<Page> pages) {
        if (pages != null)
            cacheManager.putPageUrlsToDiskCache(getChapterCacheKey(chapterUrl), pages);
    }

    protected List<Page> convertToPages(List<String> pageUrls) {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < pageUrls.size(); i++) {
            pages.add(new Page(i, pageUrls.get(i)));
        }
        return pages;
    }

    protected List<Page> getFirstImageFromPageUrls(List<String> pageUrls, String unparsedHtml) {
        List<Page> pages = convertToPages(pageUrls);
        String firstImage = parseHtmlToImageUrl(unparsedHtml);
        pages.get(0).setImageUrl(firstImage);
        return pages;
    }

    protected String getChapterCacheKey(String chapterUrl) {
        return getSourceId() + chapterUrl;
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
