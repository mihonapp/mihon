package eu.kanade.mangafeed.sources;


import com.squareup.okhttp.Headers;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.data.helpers.NetworkHelper;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.Page;
import rx.Observable;
import rx.schedulers.Schedulers;

public abstract class Source {

    // Methods to implement or optionally override

    // Name of the source to display
    public abstract String getName();

    // Id of the source (must be declared and obtained from SourceManager to avoid conflicts)
    public abstract int getSourceId();

    protected abstract String getUrlFromPageNumber(int page);
    protected abstract String getSearchUrl(String query, int page);
    protected abstract List<Manga> parsePopularMangasFromHtml(String unparsedHtml);
    protected abstract List<Manga> parseSearchFromHtml(String unparsedHtml);
    protected abstract Manga parseHtmlToManga(String mangaUrl, String unparsedHtml);
    protected abstract List<Chapter> parseHtmlToChapters(String unparsedHtml);
    protected abstract List<String> parseHtmlToPageUrls(String unparsedHtml);
    protected abstract String parseHtmlToImageUrl(String unparsedHtml);

    // Get the URL to the details of a manga, useful if the source provides some kind of API or fast calls
    protected String getMangaUrl(String defaultMangaUrl) {
        return defaultMangaUrl;
    }

    // Default headers, it can be overriden by children or just add new keys
    protected Headers.Builder headersBuilder() {
        Headers.Builder builder = new Headers.Builder();
        builder.add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)");
        return builder;
    }

    // Number of images to download at the same time
    protected int getNumberOfConcurrentImageDownloads() {
        return 3;
    }


    // ***** Source class implementation *****

    protected NetworkHelper mNetworkService;
    protected CacheManager mCacheManager;
    protected Headers mRequestHeaders;

    public Source(NetworkHelper networkService, CacheManager cacheManager) {
        mNetworkService = networkService;
        mCacheManager = cacheManager;
        mRequestHeaders = headersBuilder().build();
    }

    // Get the most popular mangas from the source
    public Observable<List<Manga>> pullPopularMangasFromNetwork(int page) {
        String url = getUrlFromPageNumber(page);
        return mNetworkService
                .getStringResponse(url, mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                .flatMap(response -> Observable.just(parsePopularMangasFromHtml(response)));
    }

    // Get mangas from the source with a query
    public Observable<List<Manga>> searchMangasFromNetwork(String query, int page) {
        return mNetworkService
                .getStringResponse(getSearchUrl(query, page), mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                .flatMap(response -> Observable.just(parseSearchFromHtml(response)));
    }

    // Get manga details from the source
    public Observable<Manga> pullMangaFromNetwork(final String mangaUrl) {
        return mNetworkService
                .getStringResponse(getMangaUrl(mangaUrl), mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                .flatMap(unparsedHtml -> Observable.just(parseHtmlToManga(mangaUrl, unparsedHtml)));
    }

    // Get chapter list of a manga from the source
    public Observable<List<Chapter>> pullChaptersFromNetwork(String mangaUrl) {
        return mNetworkService
                .getStringResponse(mangaUrl, mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                .flatMap(unparsedHtml ->
                        Observable.just(parseHtmlToChapters(unparsedHtml)));
    }

    public Observable<List<Page>> pullPageListFromNetwork(final String chapterUrl) {
        return mCacheManager.getPageUrlsFromDiskCache(chapterUrl)
                .onErrorResumeNext(throwable -> {
                    return mNetworkService
                            .getStringResponse(chapterUrl, mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                            .flatMap(unparsedHtml -> Observable.just(parseHtmlToPageUrls(unparsedHtml)))
                            .flatMap(this::convertToPages)
                            .doOnNext(pages -> savePageList(chapterUrl, pages));
                })
                .onBackpressureBuffer();
    }

    // Get the URLs of the images of a chapter
    public Observable<Page> getRemainingImageUrlsFromPageList(final List<Page> pages) {
        return Observable.from(pages)
                .filter(page -> page.getImageUrl() == null)
                .buffer(getNumberOfConcurrentImageDownloads())
                .concatMap(batchedPages -> {
                    List<Observable<Page>> pageObservable = new ArrayList<>();
                    for (Page page : batchedPages) {
                        pageObservable.add(getImageUrlFromPage(page));
                    }
                    return Observable.merge(pageObservable);
                });
    }

    private Observable<Page> getImageUrlFromPage(final Page page) {
        return mNetworkService
                .getStringResponse(page.getUrl(), mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                .flatMap(unparsedHtml -> Observable.just(parseHtmlToImageUrl(unparsedHtml)))
                .flatMap(imageUrl -> {
                    page.setImageUrl(imageUrl);
                    return Observable.just(page);
                })
                .subscribeOn(Schedulers.io());
    }

    public void savePageList(String chapterUrl, List<Page> pages) {
        mCacheManager.putPageUrlsToDiskCache(chapterUrl, pages);
    }

    private Observable<List<Page>> convertToPages(List<String> pageUrls) {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < pageUrls.size(); i++) {
            pages.add(new Page(i, pageUrls.get(i)));
        }
        return Observable.just(pages);
    }


}
