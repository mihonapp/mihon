package eu.kanade.mangafeed.sources.base;


import android.content.Context;

import com.squareup.okhttp.Headers;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.data.helpers.NetworkHelper;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.Page;
import rx.Observable;
import rx.schedulers.Schedulers;

public abstract class Source extends BaseSource {

    @Inject protected NetworkHelper mNetworkService;
    @Inject protected CacheManager mCacheManager;
    @Inject protected PreferencesHelper prefs;
    protected Headers mRequestHeaders;

    public Source(Context context) {
        App.get(context).getComponent().inject(this);
        mRequestHeaders = headersBuilder().build();
    }

    // Get the most popular mangas from the source
    public Observable<List<Manga>> pullPopularMangasFromNetwork(int page) {
        String url = getUrlFromPageNumber(page);
        return mNetworkService
                .getStringResponse(url, mRequestHeaders, null)
                .flatMap(response -> Observable.just(parsePopularMangasFromHtml(response)));
    }

    // Get mangas from the source with a query
    public Observable<List<Manga>> searchMangasFromNetwork(String query, int page) {
        return mNetworkService
                .getStringResponse(getSearchUrl(query, page), mRequestHeaders, null)
                .flatMap(response -> Observable.just(parseSearchFromHtml(response)));
    }

    // Get manga details from the source
    public Observable<Manga> pullMangaFromNetwork(final String mangaUrl) {
        return mNetworkService
                .getStringResponse(overrideMangaUrl(mangaUrl), mRequestHeaders, null)
                .flatMap(unparsedHtml -> Observable.just(parseHtmlToManga(mangaUrl, unparsedHtml)));
    }

    // Get chapter list of a manga from the source
    public Observable<List<Chapter>> pullChaptersFromNetwork(String mangaUrl) {
        return mNetworkService
                .getStringResponse(mangaUrl, mRequestHeaders, null)
                .flatMap(unparsedHtml ->
                        Observable.just(parseHtmlToChapters(unparsedHtml)));
    }

    public Observable<List<Page>> pullPageListFromNetwork(final String chapterUrl) {
        return mCacheManager.getPageUrlsFromDiskCache(chapterUrl)
                .onErrorResumeNext(throwable -> {
                    return mNetworkService
                            .getStringResponse(overrideChapterPageUrl(chapterUrl), mRequestHeaders, null)
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
        page.setStatus(Page.LOAD_PAGE);
        return mNetworkService
                .getStringResponse(overrideRemainingPagesUrl(page.getUrl()), mRequestHeaders, null)
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
        Observable<Page> obs = Observable.just(page);
        if (page.getImageUrl() == null)
            return obs;

        if (!mCacheManager.isImageInCache(page.getImageUrl())) {
            page.setStatus(Page.DOWNLOAD_IMAGE);
            obs = cacheImage(page);
        }

        return obs.flatMap(p -> {
            page.setImagePath(mCacheManager.getImagePath(page.getImageUrl()));
            page.setStatus(Page.READY);
            return Observable.just(page);
        }).onErrorResumeNext(e -> {
            page.setStatus(Page.ERROR);
            return Observable.just(page);
        });
    }

    private Observable<Page> cacheImage(final Page page) {
        return mNetworkService.getProgressResponse(page.getImageUrl(), mRequestHeaders, page)
                .flatMap(resp -> {
                    if (!mCacheManager.putImageToDiskCache(page.getImageUrl(), resp)) {
                        throw new IllegalStateException("Unable to save image");
                    }
                    return Observable.just(page);
                });
    }

    public void savePageList(String chapterUrl, List<Page> pages) {
        if (pages != null)
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
