package eu.kanade.tachiyomi.data.source.online.english;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.Language;
import eu.kanade.tachiyomi.data.source.LanguageKt;
import eu.kanade.tachiyomi.data.source.base.Source;
import eu.kanade.tachiyomi.data.source.model.MangasPage;
import eu.kanade.tachiyomi.util.Parser;
import okhttp3.Headers;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

public class ReadMangaToday extends Source {
    public static final String NAME = "ReadMangaToday";
    public static final String BASE_URL = "http://www.readmanga.today";
    public static final String POPULAR_MANGAS_URL = BASE_URL + "/hot-manga/%s";
    public static final String SEARCH_URL = BASE_URL + "/service/search?q=%s";

    private static JsonParser parser = new JsonParser();
    private static Gson gson = new Gson();

    public ReadMangaToday(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    protected String getInitialPopularMangasUrl() {
        return String.format(POPULAR_MANGAS_URL, "");
    }

    @Override
    protected String getInitialSearchUrl(String query) {
        return String.format(SEARCH_URL, Uri.encode(query), 1);
    }

    @Override
    public Language getLang() {
        return LanguageKt.getEN();
    }

    @Override
    public List<Manga> parsePopularMangasFromHtml(Document parsedHtml) {
        List<Manga> mangaList = new ArrayList<>();

        for (Element currentHtmlBlock : parsedHtml.select("div.hot-manga > div.style-list > div.box")) {
            Manga currentManga = constructPopularMangaFromHtmlBlock(currentHtmlBlock);
            mangaList.add(currentManga);
        }
        return mangaList;
    }

    private Manga constructPopularMangaFromHtmlBlock(Element htmlBlock) {
        Manga manga = new Manga();
        manga.source = getId();

        Element urlElement = Parser.element(htmlBlock, "div.title > h2 > a");
        if (urlElement != null) {
            manga.setUrl(urlElement.attr("href"));
            manga.title = urlElement.attr("title");
        }
        return manga;
    }

    @Override
    protected String parseNextPopularMangasUrl(Document parsedHtml, MangasPage page) {
        Element next = Parser.element(parsedHtml, "div.hot-manga > ul.pagination > li > a:contains(»)");
        return next != null ? next.attr("href") : null;
    }

    @Override
    public Observable<MangasPage> searchMangasFromNetwork(final MangasPage page, String query) {
        return networkService
                .requestBody(searchMangaRequest(page, query), true)
                .doOnNext(new Action1<String>() {
                    @Override
                    public void call(String doc) {
                        page.mangas = ReadMangaToday.this.parseSearchFromJson(doc);
                    }
                })
                .map(new Func1<String, MangasPage>() {
                    @Override
                    public MangasPage call(String response) {
                        return page;
                    }
                });
    }

    @Override
    protected Headers.Builder headersBuilder() {
        return super.headersBuilder().add("X-Requested-With", "XMLHttpRequest");
    }

    protected List<Manga> parseSearchFromJson(String unparsedJson) {
        List<Manga> mangaList = new ArrayList<>();

        JsonArray mangasArray = parser.parse(unparsedJson).getAsJsonArray();

        for (JsonElement mangaElement : mangasArray) {
            Manga currentManga = constructSearchMangaFromJsonObject(mangaElement.getAsJsonObject());
            mangaList.add(currentManga);
        }
        return mangaList;
    }

    private Manga constructSearchMangaFromJsonObject(JsonObject jsonObject) {
        Manga manga = new Manga();
        manga.source = getId();

        manga.setUrl(gson.fromJson(jsonObject.get("url"), String.class));
        manga.title = gson.fromJson(jsonObject.get("title"), String.class);

        return manga;
    }

    @Override
    protected List<Manga> parseSearchFromHtml(Document parsedHtml) {
        return null;
    }

    @Override
    protected String parseNextSearchUrl(Document parsedHtml, MangasPage page, String query) {
        return null;
    }

    public Manga parseHtmlToManga(String mangaUrl, String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<!-- content start -->");
        int endIndex = unparsedHtml.indexOf("<!-- /content-end -->", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);
        Element detailElement = parsedDocument.select("div.movie-meta").first();

        Manga manga = Manga.create(mangaUrl);
        for (Element castHtmlBlock : parsedDocument.select("div.cast ul.cast-list > li")) {
            String name = Parser.text(castHtmlBlock, "ul > li > a");
            String role = Parser.text(castHtmlBlock, "ul > li:eq(1)");
            if (role.equals("Author")) {
                manga.author = name;
            } else if (role.equals("Artist")) {
                manga.artist = name;
            }
        }

        String description = Parser.text(detailElement, "li.movie-detail");
        if (description != null) {
            manga.description = description;
        }
        String genres = Parser.text(detailElement, "dl.dl-horizontal > dd:eq(5)");
        if (genres != null) {
            manga.genre = genres;
        }
        manga.status = parseStatus(Parser.text(detailElement, "dl.dl-horizontal > dd:eq(3)"));
        manga.thumbnail_url = Parser.src(detailElement, "img.img-responsive");

        manga.initialized = true;
        return manga;
    }

    private int parseStatus(String status) {
        if (status.contains("Ongoing")) {
            return Manga.ONGOING;
        } else if (status.contains("Completed")) {
            return Manga.COMPLETED;
        }
        return Manga.UNKNOWN;
    }

    @Override
    public List<Chapter> parseHtmlToChapters(String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<!-- content start -->");
        int endIndex = unparsedHtml.indexOf("<!-- /content-end -->", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        List<Chapter> chapterList = new ArrayList<>();

        for (Element chapterElement : parsedDocument.select("ul.chp_lst > li")) {
            Chapter currentChapter = constructChapterFromHtmlBlock(chapterElement);
            chapterList.add(currentChapter);
        }
        return chapterList;
    }

    private Chapter constructChapterFromHtmlBlock(Element chapterElement) {
        Chapter chapter = Chapter.create();

        Element urlElement = chapterElement.select("a").first();
        Element dateElement = chapterElement.select("span.dte").first();

        if (urlElement != null) {
            chapter.setUrl(urlElement.attr("href"));
            chapter.name = urlElement.select("span.val").text();
        }
        if (dateElement != null) {
            chapter.date_upload = parseDateFromElement(dateElement);
        }
        return chapter;
    }

    private long parseDateFromElement(Element dateElement) {
        String dateAsString = dateElement.text();
        String[] dateWords = dateAsString.split(" ");

        if (dateWords.length == 3) {
            int timeAgo = Integer.parseInt(dateWords[0]);
            Calendar date = Calendar.getInstance();

            if (dateWords[1].contains("Minute")) {
                date.add(Calendar.MINUTE, - timeAgo);
            } else if (dateWords[1].contains("Hour")) {
                date.add(Calendar.HOUR_OF_DAY, - timeAgo);
            } else if (dateWords[1].contains("Day")) {
                date.add(Calendar.DAY_OF_YEAR, -timeAgo);
            } else if (dateWords[1].contains("Week")) {
                date.add(Calendar.WEEK_OF_YEAR, -timeAgo);
            } else if (dateWords[1].contains("Month")) {
                date.add(Calendar.MONTH, -timeAgo);
            } else if (dateWords[1].contains("Year")) {
                date.add(Calendar.YEAR, -timeAgo);
            }

            return date.getTimeInMillis();
        }

        return 0;
    }

    @Override
    public List<String> parseHtmlToPageUrls(String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<!-- content start -->");
        int endIndex = unparsedHtml.indexOf("<!-- /content-end -->", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        List<String> pageUrlList = new ArrayList<>();

        Elements pageUrlElements = parsedDocument.select("ul.list-switcher-2 > li > select.jump-menu").first().getElementsByTag("option");
        for (Element pageUrlElement : pageUrlElements) {
            pageUrlList.add(pageUrlElement.attr("value"));
        }

        return pageUrlList;
    }

    @Override
    public String parseHtmlToImageUrl(String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<!-- content start -->");
        int endIndex = unparsedHtml.indexOf("<!-- /content-end -->", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        Element imageElement = Parser.element(parsedDocument, "img.img-responsive-2");

        return imageElement.attr("src");
    }

}
