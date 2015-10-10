package eu.kanade.mangafeed.sources;


import com.squareup.okhttp.Headers;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.data.helpers.NetworkHelper;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import rx.Observable;
import rx.schedulers.Schedulers;

public abstract class Source {

    protected NetworkHelper mNetworkService;
    protected CacheManager mCacheManager;
    protected Headers mRequestHeaders;

    public Source(NetworkHelper networkService, CacheManager cacheManager) {
        mNetworkService = networkService;
        mCacheManager = cacheManager;
        mRequestHeaders = headersBuilder().build();
    }

    // Default headers, it can be overriden by children or add new keys
    protected Headers.Builder headersBuilder() {
        Headers.Builder builder = new Headers.Builder();
        builder.add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)");
        return builder;
    }

    // Get the most popular mangas from the source
    public Observable<List<Manga>> pullPopularMangasFromNetwork(int page) {
        String url = getUrlFromPageNumber(page);
        return mNetworkService
                .getStringResponse(url, mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                .flatMap(response -> Observable.just(parsePopularMangasFromHtml(response)));
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

    // Get the URLs of the images of a chapter
    public Observable<String> getImageUrlsFromNetwork(final String chapterUrl) {
        return mNetworkService
                .getStringResponse(chapterUrl, mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                .flatMap(unparsedHtml -> Observable.from(parseHtmlToPageUrls(unparsedHtml)))
                .buffer(3)
                .concatMap(batchedPageUrls -> {
                    List<Observable<String>> imageUrlObservables = new ArrayList<>();
                    for (String pageUrl : batchedPageUrls) {
                        Observable<String> temporaryObservable = mNetworkService
                                .getStringResponse(pageUrl, mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                                .flatMap(unparsedHtml -> Observable.just(parseHtmlToImageUrl(unparsedHtml)))
                                .subscribeOn(Schedulers.io());

                        imageUrlObservables.add(temporaryObservable);
                    }

                    return Observable.merge(imageUrlObservables);
                });
    }

    // Store the URLs of a chapter in the cache
    public Observable<String> pullImageUrlsFromNetwork(final String chapterUrl) {
        final List<String> temporaryCachedImageUrls = new ArrayList<>();

        return mCacheManager.getImageUrlsFromDiskCache(chapterUrl)
                .onErrorResumeNext(throwable -> {
                    return getImageUrlsFromNetwork(chapterUrl)
                            .doOnNext(imageUrl -> temporaryCachedImageUrls.add(imageUrl))
                            .doOnCompleted(mCacheManager.putImageUrlsToDiskCache(chapterUrl, temporaryCachedImageUrls));
                })
                .onBackpressureBuffer();
    }

    // Get the URL to the details of a manga, useful if the source provides some kind of API or fast calls
    protected String getMangaUrl(String defaultMangaUrl) {
        return defaultMangaUrl;
    }

    public abstract String getName();
    public abstract int getSource();

    protected abstract String getUrlFromPageNumber(int page);
    protected abstract List<Manga> parsePopularMangasFromHtml(String unparsedHtml);
    protected abstract Manga parseHtmlToManga(String mangaUrl, String unparsedHtml);
    protected abstract List<Chapter> parseHtmlToChapters(String unparsedHtml);
    protected abstract List<String> parseHtmlToPageUrls(String unparsedHtml);
    protected abstract String parseHtmlToImageUrl(String unparsedHtml);


}
