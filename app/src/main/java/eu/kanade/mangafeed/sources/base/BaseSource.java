package eu.kanade.mangafeed.sources.base;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Response;

import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import rx.Observable;

public abstract class BaseSource {

    // Name of the source to display
    public abstract String getName();

    // Id of the source (must be declared and obtained from SourceManager to avoid conflicts)
    public abstract int getSourceId();

    // True if the source requires a login
    public abstract boolean isLoginRequired();

    // Given a page number, it should return the URL of the page where the manga list is found
    protected abstract String getUrlFromPageNumber(int page);

    // From the URL obtained before, this method must return a list of mangas
    protected abstract List<Manga> parsePopularMangasFromHtml(String unparsedHtml);

    // Given a query and a page number, return the URL of the results
    protected abstract String getSearchUrl(String query, int page);

    // From the URL obtained before, this method must return a list of mangas
    protected abstract List<Manga> parseSearchFromHtml(String unparsedHtml);

    // Given the URL of a manga and the result of the request, return the details of the manga
    protected abstract Manga parseHtmlToManga(String mangaUrl, String unparsedHtml);

    // Given the result of the request to mangas' chapters, return a list of chapters
    protected abstract List<Chapter> parseHtmlToChapters(String unparsedHtml);

    // Given the result of the request to a chapter, return the list of URLs of the chapter
    protected abstract List<String> parseHtmlToPageUrls(String unparsedHtml);

    // Given the result of the request to a chapter's page, return the URL of the image of the page
    protected abstract String parseHtmlToImageUrl(String unparsedHtml);


    // Login related methods, shouldn't be overriden if the source doesn't require it
    public Observable<Boolean> login(String username, String password) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isLogged() {
        throw new UnsupportedOperationException("Not implemented");
    }

    protected boolean isAuthenticationSuccessful(Response response) {
        throw new UnsupportedOperationException("Not implemented");
    }
    

    // Default fields, they can be overriden by sources' implementation

    // Get the URL to the details of a manga, useful if the source provides some kind of API or fast calls
    protected String overrideMangaUrl(String defaultMangaUrl) {
        return defaultMangaUrl;
    }

    // Get the URL of the first page that contains a source image and the page list
    protected String overrideChapterPageUrl(String defaultPageUrl) {
        return defaultPageUrl;
    }

    // Get the URL of the remaining pages that contains source images
    protected String overrideRemainingPagesUrl(String defaultPageUrl) {
        return defaultPageUrl;
    }

    // Default headers, it can be overriden by children or just add new keys
    protected Headers.Builder headersBuilder() {
        Headers.Builder builder = new Headers.Builder();
        builder.add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)");
        return builder;
    }

    // Number of images to download at the same time. 3 by default
    protected int overrideNumberOfConcurrentPageDownloads() {
        return 3;
    }

}
