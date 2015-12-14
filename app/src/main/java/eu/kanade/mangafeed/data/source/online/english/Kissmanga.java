package eu.kanade.mangafeed.data.source.online.english;

import android.content.Context;
import android.net.Uri;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.source.model.MangasPage;
import eu.kanade.mangafeed.data.source.model.Page;
import rx.Observable;

public class Kissmanga extends Source {

    public static final String NAME = "Kissmanga (EN)";
    public static final String HOST = "kissmanga.com";
    public static final String IP = "93.174.95.110";
    public static final String BASE_URL = "http://" + IP;
    public static final String POPULAR_MANGAS_URL = BASE_URL + "/MangaList/MostPopular?page=%s";
    public static final String SEARCH_URL = BASE_URL + "/AdvanceSearch";

    public Kissmanga(Context context) {
        super(context);
    }

    @Override
    protected Headers.Builder headersBuilder() {
        Headers.Builder builder = super.headersBuilder();
        builder.add("Host", HOST);
        return builder;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getId() {
        return SourceManager.KISSMANGA;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public boolean isLoginRequired() {
        return false;
    }

    @Override
    protected String getInitialPopularMangasUrl() {
        return String.format(POPULAR_MANGAS_URL, 1);
    }

    @Override
    protected String getInitialSearchUrl(String query) {
        return SEARCH_URL;
    }

    @Override
    protected List<Manga> parsePopularMangasFromHtml(Document parsedHtml) {
        List<Manga> mangaList = new ArrayList<>();

        Elements mangaHtmlBlocks = parsedHtml.select("table.listing tr:gt(1)");
        for (Element currentHtmlBlock : mangaHtmlBlocks) {
            Manga currentManga = constructPopularMangaFromHtmlBlock(currentHtmlBlock);
            mangaList.add(currentManga);
        }

        return mangaList;
    }

    private Manga constructPopularMangaFromHtmlBlock(Element htmlBlock) {
        Manga mangaFromHtmlBlock = new Manga();
        mangaFromHtmlBlock.source = getId();

        Element urlElement = htmlBlock.select("td a:eq(0)").first();

        if (urlElement != null) {
            mangaFromHtmlBlock.setUrl(urlElement.attr("href"));
            mangaFromHtmlBlock.title = urlElement.text();
        }

        return mangaFromHtmlBlock;
    }

    @Override
    protected String parseNextPopularMangasUrl(Document parsedHtml, MangasPage page) {
        Element next = parsedHtml.select("li > a:contains(› Next)").first();
        if (next == null)
            return null;

        return BASE_URL + next.attr("href");
    }

    public Observable<MangasPage> searchMangasFromNetwork(MangasPage page, String query) {
        if (page.page == 1)
            page.url = getInitialSearchUrl(query);

        FormEncodingBuilder form = new FormEncodingBuilder();
        form.add("authorArtist", "");
        form.add("mangaName", query);
        form.add("status", "");
        form.add("genres", "");

        return networkService
                .postData(page.url, form.build(), requestHeaders)
                .flatMap(networkService::mapResponseToString)
                .map(Jsoup::parse)
                .doOnNext(doc -> page.mangas = parseSearchFromHtml(doc))
                .doOnNext(doc -> page.nextPageUrl = parseNextSearchUrl(doc, page, query))
                .map(response -> page);
    }

    @Override
    protected List<Manga> parseSearchFromHtml(Document parsedHtml) {
        return parsePopularMangasFromHtml(parsedHtml);
    }

    @Override
    protected String parseNextSearchUrl(Document parsedHtml, MangasPage page, String query) {
        return null;
    }

    @Override
    protected Manga parseHtmlToManga(String mangaUrl, String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        Element infoElement = parsedDocument.select("div.barContent").first();
        Element titleElement = infoElement.select("a.bigChar").first();
        Element authorElement = infoElement.select("p:has(span:contains(Author:)) > a").first();
        Elements genreElement = infoElement.select("p:has(span:contains(Genres:)) > *:gt(0)");
        Elements descriptionElement = infoElement.select("p:has(span:contains(Summary:)) ~ p");
        Element thumbnailUrlElement = parsedDocument.select(".rightBox:eq(0) img").first();

        Manga newManga = new Manga();
        newManga.url = mangaUrl;

        if (titleElement != null) {
            newManga.title = titleElement.text();
        }
        if (authorElement != null) {
            newManga.author = authorElement.text();
        }
        if (descriptionElement != null) {
            newManga.description = descriptionElement.text();
        }
        if (genreElement != null) {
            newManga.genre = genreElement.text();
        }
        if (thumbnailUrlElement != null) {
            newManga.thumbnail_url = Uri.parse(thumbnailUrlElement.attr("src"))
                    .buildUpon().authority(IP).toString();
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
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<Chapter> chapterList = new ArrayList<>();

        Elements chapterElements = parsedDocument.select("table.listing tr:gt(1)");
        for (Element chapterElement : chapterElements) {
            Chapter currentChapter = constructChapterFromHtmlBlock(chapterElement);

            chapterList.add(currentChapter);
        }

        return chapterList;
    }

    private Chapter constructChapterFromHtmlBlock(Element chapterElement) {
        Chapter newChapter = Chapter.create();

        Element urlElement = chapterElement.select("a").first();
        Element dateElement = chapterElement.select("td:eq(1)").first();

        if (urlElement != null) {
            newChapter.setUrl(urlElement.attr("href"));
            newChapter.name = urlElement.text();
        }
        if (dateElement != null) {
            try {
                newChapter.date_upload = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH).parse(dateElement.text()).getTime();
            } catch (ParseException e) {
                // Do Nothing.
            }
        }

        newChapter.date_fetch = new Date().getTime();

        return newChapter;
    }

    public Observable<List<Page>> pullPageListFromNetwork(final String chapterUrl) {
        FormEncodingBuilder builder = new FormEncodingBuilder();
        return networkService
                .postData(getBaseUrl() + overrideChapterUrl(chapterUrl), builder.build(), requestHeaders)
                .flatMap(networkService::mapResponseToString)
                .flatMap(unparsedHtml -> {
                    List<String> pageUrls = parseHtmlToPageUrls(unparsedHtml);
                    return Observable.just(getFirstImageFromPageUrls(pageUrls, unparsedHtml));
                });
    }

    @Override
    protected List<String> parseHtmlToPageUrls(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);
        List<String> pageUrlList = new ArrayList<>();

        int numImages = parsedDocument.select("#divImage img").size();

        for (int i = 0; i < numImages; i++) {
            pageUrlList.add("");
        }
        return pageUrlList;
    }

    @Override
    protected List<Page> getFirstImageFromPageUrls(List<String> pageUrls, String unparsedHtml) {
        List<Page> pages = convertToPages(pageUrls);

        Pattern p = Pattern.compile("lstImages.push\\(\"(.+?)\"");
        Matcher m = p.matcher(unparsedHtml);
        List<String> imageUrls = new ArrayList<>();
        while (m.find()) {
            imageUrls.add(m.group(1));
        }

        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setImageUrl(imageUrls.get(i));
        }
        return pages;
    }

    @Override
    protected String parseHtmlToImageUrl(String unparsedHtml) {
        return null;
    }

    @Override
    public Observable<Response> getImageProgressResponse(final Page page) {
        return networkService.getProgressResponse(page.getImageUrl(), null, page);
    }

}
