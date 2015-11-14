package eu.kanade.mangafeed.sources;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.base.Source;

public class Mangafox extends Source {

    public static final String NAME = "Mangafox (EN)";

    private static final String INITIAL_UPDATE_URL = "http://mangafox.me/directory/";
    private static final String INITIAL_SEARCH_URL =
            "http://mangafox.me/search.php?name_method=cw&advopts=1&order=az&sort=name";

    public Mangafox(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getSourceId() {
        return SourceManager.MANGAFOX;
    }

    @Override
    public boolean isLoginRequired() {
        return false;
    }

    @Override
    protected String getUrlFromPageNumber(int page) {
        return INITIAL_UPDATE_URL + page + ".htm";
    }

    @Override
    public List<Manga> parsePopularMangasFromHtml(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<Manga> mangaList = new ArrayList<>();

        Elements mangaHtmlBlocks = parsedDocument.select("div#mangalist > ul.list > li");
        for (Element currentHtmlBlock : mangaHtmlBlocks) {
            Manga currentManga = constructPopularMangaFromHtmlBlock(currentHtmlBlock);
            mangaList.add(currentManga);
        }

        return mangaList;
    }

    private Manga constructPopularMangaFromHtmlBlock(Element htmlBlock) {
        Manga mangaFromHtmlBlock = new Manga();
        mangaFromHtmlBlock.source = getSourceId();

        Element urlElement = htmlBlock.select("a.title").first();

        if (urlElement != null) {
            mangaFromHtmlBlock.url = urlElement.attr("href");
            mangaFromHtmlBlock.title = urlElement.text();
        }

        return mangaFromHtmlBlock;
    }

    @Override
    protected String getSearchUrl(String query, int page) {
        return INITIAL_SEARCH_URL + "&name=" + query + "&page=" + page;
    }

    @Override
    protected List<Manga> parseSearchFromHtml(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<Manga> mangaList = new ArrayList<>();

        Elements mangaHtmlBlocks = parsedDocument.select("table#listing > tbody > tr:gt(0)");
        for (Element currentHtmlBlock : mangaHtmlBlocks) {
            Manga currentManga = constructSearchMangaFromHtmlBlock(currentHtmlBlock);
            mangaList.add(currentManga);
        }

        return mangaList;
    }

    private Manga constructSearchMangaFromHtmlBlock(Element htmlBlock) {
        Manga mangaFromHtmlBlock = new Manga();
        mangaFromHtmlBlock.source = getSourceId();

        Element urlElement = htmlBlock.select("a.series_preview").first();

        if (urlElement != null) {
            mangaFromHtmlBlock.url = urlElement.attr("href");
            mangaFromHtmlBlock.title = urlElement.text();
        }

        return mangaFromHtmlBlock;
    }

    @Override
    protected Manga parseHtmlToManga(String mangaUrl, String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        Element infoElement = parsedDocument.select("div#title").first();
        Element titleElement = infoElement.select("h2 > a").first();
        Element rowElement = infoElement.select("table > tbody > tr:eq(1)").first();
        Element authorElement = rowElement.select("td:eq(1)").first();
        Element artistElement = rowElement.select("td:eq(2)").first();
        Element genreElement = rowElement.select("td:eq(3)").first();
        Element descriptionElement = infoElement.select("p.summary").first();
        Element thumbnailUrlElement = parsedDocument.select("div.cover > img").first();

        Manga newManga = new Manga();
        newManga.url = mangaUrl;

        if (titleElement != null) {
            String title = titleElement.text();
            // Strip the last word
            title = title.substring(0, title.lastIndexOf(" "));
            newManga.title = title;
        }
        if (artistElement != null) {
            String fieldArtist = artistElement.text();
            newManga.artist = fieldArtist;
        }
        if (authorElement != null) {
            String fieldAuthor = authorElement.text();
            newManga.author = fieldAuthor;
        }
        if (descriptionElement != null) {
            String fieldDescription = descriptionElement.text();
            newManga.description = fieldDescription;
        }
        if (genreElement != null) {
            String fieldGenre = genreElement.text();
            newManga.genre = fieldGenre;
        }
        if (thumbnailUrlElement != null) {
            String fieldThumbnailUrl = thumbnailUrlElement.attr("src");
            newManga.thumbnail_url = fieldThumbnailUrl;
        }
//        if (statusElement != null) {
//            boolean fieldCompleted = statusElement.text().contains("Completed");
//            newManga.status = fieldCompleted + "";
//        }

        newManga.initialized = true;

        return newManga;
    }

    @Override
    protected List<Chapter> parseHtmlToChapters(String unparsedHtml) {
        return null;
    }

    @Override
    protected List<String> parseHtmlToPageUrls(String unparsedHtml) {
        return null;
    }

    @Override
    protected String parseHtmlToImageUrl(String unparsedHtml) {
        return null;
    }
}
