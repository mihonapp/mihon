package eu.kanade.tachiyomi.data.source.online.russian;

import android.content.Context;
import android.net.Uri;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.Language;
import eu.kanade.tachiyomi.data.source.LanguageKt;
import eu.kanade.tachiyomi.data.source.base.Source;
import eu.kanade.tachiyomi.data.source.model.MangasPage;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.util.Parser;

public class Readmanga extends Source {

    public static final String NAME = "Readmanga";
    public static final String BASE_URL = "http://readmanga.me";
    public static final String POPULAR_MANGAS_URL = BASE_URL + "/list?sortType=rate";
    public static final String SEARCH_URL = BASE_URL + "/search?q=%s";

    public Readmanga(Context context) {
        super(context);
    }

    @Override
    public Language getLang() {
        return LanguageKt.getRU();
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
        return POPULAR_MANGAS_URL;
    }

    @Override
    protected String getInitialSearchUrl(String query) {
        return String.format(SEARCH_URL, Uri.encode(query));
    }

    @Override
    protected List<Manga> parsePopularMangasFromHtml(Document parsedHtml) {
        List<Manga> mangaList = new ArrayList<>();

        for (Element currentHtmlBlock : parsedHtml.select("div.desc")) {
            Manga manga = constructPopularMangaFromHtml(currentHtmlBlock);
            mangaList.add(manga);
        }

        return mangaList;
    }

    private Manga constructPopularMangaFromHtml(Element currentHtmlBlock) {
        Manga manga = new Manga();
        manga.source = getId();

        Element urlElement = currentHtmlBlock.getElementsByTag("h3").select("a").first();

        if (urlElement != null) {
            manga.setUrl(urlElement.attr("href"));
            manga.title = urlElement.text();
        }

        return manga;
    }

    @Override
    protected String parseNextPopularMangasUrl(Document parsedHtml, MangasPage page) {
        String path = Parser.href(parsedHtml, "a:contains(→)");
        return path != null ? BASE_URL + path : null;
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
        Element infoElement = parsedDocument.select("div.leftContent").first();

        Manga manga = Manga.create(mangaUrl);
        manga.title = Parser.text(infoElement, "span.eng-name");
        manga.author = Parser.text(infoElement, "span.elem_author ");
        manga.genre = Parser.allText(infoElement, "span.elem_genre ").replaceAll(" ,", ",");
        manga.description = Parser.allText(infoElement, "div.manga-description");
        if (Parser.text(infoElement, "h1.names:contains(Сингл)") != null) {
            manga.status = Manga.COMPLETED;
        } else {
            manga.status = parseStatus(Parser.text(infoElement, "p:has(b:contains(Перевод:))"));
        }

        String thumbnail = Parser.element(infoElement, "img").attr("data-full");
        if (thumbnail != null) {
            manga.thumbnail_url = thumbnail;
        }

        manga.initialized = true;
        return manga;
    }

    private int parseStatus(String status) {
        if (status.contains("продолжается")) {
            return Manga.ONGOING;
        }
        if (status.contains("завершен")) {
            return Manga.COMPLETED;
        }
        return Manga.UNKNOWN;
    }

    @Override
    protected List<Chapter> parseHtmlToChapters(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);
        List<Chapter> chapterList = new ArrayList<>();

        for (Element chapterElement : parsedDocument.select("div.chapters-link tbody tr")) {
            Chapter chapter = constructChapterFromHtmlBlock(chapterElement);
            chapterList.add(chapter);
        }

        return chapterList;
    }

    private Chapter constructChapterFromHtmlBlock(Element chapterElement) {
        Chapter chapter = Chapter.create();

        Element urlElement = Parser.element(chapterElement, "a");
        String date = Parser.text(chapterElement, "td:eq(1)");

        if (urlElement != null) {
            chapter.setUrl(urlElement.attr("href") + "?mature=1");
            chapter.name = urlElement.text().replaceAll(" новое", "");
        }

        if (date != null) {
            try {
                chapter.date_upload = new SimpleDateFormat("dd/MM/yy", Locale.ENGLISH).parse(date).getTime();
            } catch (ParseException e) { /* Ignore */ }
        }
        return chapter;
    }

    // Without this extra chapters are in the wrong place in the list
    @Override
    public void parseChapterNumber(Chapter chapter) {
        String url = chapter.url.replace("?mature=1", "");

        String[] urlParts = url.replaceAll("/[\\w\\d]+/vol", "").split("/");
        if (Float.parseFloat(urlParts[1]) < 1000f) {
            urlParts[1] = "0" + urlParts[1];
        }
        if (Float.parseFloat(urlParts[1]) < 100f) {
            urlParts[1] = "0" + urlParts[1];
        }
        if (Float.parseFloat(urlParts[1]) < 10f) {
            urlParts[1] = "0" + urlParts[1];
        }

        chapter.chapter_number = Float.parseFloat(urlParts[0] + "." + urlParts[1]);
    }

    @Override
    protected List<String> parseHtmlToPageUrls(String unparsedHtml) {
        ArrayList<String> pages = new ArrayList<>();

        int beginIndex = unparsedHtml.indexOf("rm_h.init( [");
        int endIndex = unparsedHtml.indexOf("], 0, false);", beginIndex);

        String trimmedHtml = unparsedHtml.substring(beginIndex + 13, endIndex);
        trimmedHtml = trimmedHtml.replaceAll("[\"']", "");
        String[] pageUrls = trimmedHtml.split("],\\[");
        for (int i = 0; i < pageUrls.length; i++) {
            pages.add("");
        }
        return pages;
    }

    @Override
    protected List<Page> parseFirstPage(List<? extends Page> pages, String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("rm_h.init( [");
        int endIndex = unparsedHtml.indexOf("], 0, false);", beginIndex);

        String trimmedHtml = unparsedHtml.substring(beginIndex + 13, endIndex);
        trimmedHtml = trimmedHtml.replaceAll("[\"']", "");
        String[] pageUrls = trimmedHtml.split("],\\[");
        for (int i = 0; i < pageUrls.length; i++) {
            String[] urlParts = pageUrls[i].split(","); // auto/12/56,http://e7.postfact.ru/,/51/01.jpg_res.jpg
            String page = urlParts[1] + urlParts[0] + urlParts[2];
            pages.get(i).setImageUrl(page);
        }
        return (List<Page>) pages;
    }

    @Override
    protected String parseHtmlToImageUrl(String unparsedHtml) {
        return null;
    }
}
