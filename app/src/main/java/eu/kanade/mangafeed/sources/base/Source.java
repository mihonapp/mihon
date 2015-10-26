package eu.kanade.mangafeed.sources.base;


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

public abstract class Source extends BaseSource {

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
                .getStringResponse(overrideMangaUrl(mangaUrl), mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
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
                            .getStringResponse(overrideChapterPageUrl(chapterUrl), mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
                            .flatMap(unparsedHtml -> {
                                List<String> pageUrls = parseHtmlToPageUrls(unparsedHtml);
                                return Observable.just(getFirstImageFromPageUrls(pageUrls, unparsedHtml));
                            })
                            .doOnNext(pages -> savePageList(chapterUrl, pages));
                })
                .onBackpressureBuffer();
    }

    // Get the URLs of the images of a chapter
    public Observable<Page> getRemainingImageUrlsFromPageList(final List<Page> pages) {
        return Observable.from(pages)
                .filter(page -> page.getImageUrl() == null)
                .window(overrideNumberOfConcurrentPageDownloads())
                .concatMap(batchedPages ->
                        batchedPages.concatMap(this::getImageUrlFromPage)
                );
    }

    private Observable<Page> getImageUrlFromPage(final Page page) {
        return mNetworkService
                .getStringResponse(overrideRemainingPagesUrl(page.getUrl()), mNetworkService.NULL_CACHE_CONTROL, mRequestHeaders)
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

    private List<Page> convertToPages(List<String> pageUrls) {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < pageUrls.size(); i++) {
            pages.add(new Page(i, pageUrls.get(i)));
        }
        return pages;
    }

    private List<Page> getFirstImageFromPageUrls(List<String> pageUrls, String unparsedHtml) {
        List<Page> pages = convertToPages(pageUrls);
        String firstImage = parseHtmlToImageUrl(unparsedHtml);
        pages.get(0).setImageUrl(firstImage);
        return pages;
    }

}
