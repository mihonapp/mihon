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

import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.source.model.MangasPage;
import eu.kanade.mangafeed.data.source.model.Page;
import rx.Observable;

public class Batoto extends Source {

    public static final String NAME = "Batoto (EN)";
    public static final String BASE_URL = "http://bato.to";
    public static final String POPULAR_MANGAS_URL = BASE_URL + "/search_ajax?order_cond=views&order=desc&p=%d";
    public static final String SEARCH_URL = BASE_URL + "/search_ajax?name=%s&p=%s";
    public static final String CHAPTER_URL = "/areader?id=%s&p=1";
    public static final String PAGE_URL = BASE_URL + "/areader?id=%s&p=%s";
    public static final String MANGA_URL = "/comic_pop?id=%s";
    public static final String LOGIN_URL = BASE_URL + "/forums/index.php?app=core&module=global&section=login";

    private Pattern datePattern;
    private Map<String, Integer> dateFields;

    public Batoto(Context context) {
        super(context);

        datePattern = Pattern.compile("(\\d+|A|An)\\s+(.*?)s? ago.*");
        dateFields = new HashMap<String, Integer>() {{
            put("second", Calendar.SECOND);
            put("minute", Calendar.MINUTE);
            put("hour",   Calendar.HOUR);
            put("day",    Calendar.DATE);
            put("week",   Calendar.WEEK_OF_YEAR);
            put("month",  Calendar.MONTH);
            put("year",   Calendar.YEAR);
        }};
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getId() {
        return SourceManager.BATOTO;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    protected Headers.Builder headersBuilder() {
        Headers.Builder builder = super.headersBuilder();
        builder.add("Cookie", "lang_option=English");
        builder.add("Referer", "http://bato.to/reader");
        return builder;
    }

    public Observable<List<String>> getGenres() {
        List<String> genres = new ArrayList<>(38);

        genres.add("4-Koma");
        genres.add("Action");
        genres.add("Adventure");
        genres.add("Award Winning");
        genres.add("Comedy");
        genres.add("Cooking");
        genres.add("Doujinshi");
        genres.add("Drama");
        genres.add("Ecchi");
        genres.add("Fantasy");
        genres.add("Gender Bender");
        genres.add("Harem");
        genres.add("Historical");
        genres.add("Horror");
        genres.add("Josei");
        genres.add("Martial Arts");
        genres.add("Mecha");
        genres.add("Medical");
        genres.add("Music");
        genres.add("Mystery");
        genres.add("One Shot");
        genres.add("Psychological");
        genres.add("Romance");
        genres.add("School Life");
        genres.add("Sci-fi");
        genres.add("Seinen");
        genres.add("Shoujo");
        genres.add("Shoujo Ai");
        genres.add("Shounen");
        genres.add("Shounen Ai");
        genres.add("Slice of Life");
        genres.add("Smut");
        genres.add("Sports");
        genres.add("Supernatural");
        genres.add("Tragedy");
        genres.add("Webtoon");
        genres.add("Yaoi");
        genres.add("Yuri");

        return Observable.just(genres);
    }

    @Override
    public boolean isLoginRequired() {
        return true;
    }

    @Override
    public String getInitialPopularMangasUrl() {
        return String.format(POPULAR_MANGAS_URL, 1);
    }

    @Override
    public String getInitialSearchUrl(String query) {
        return String.format(SEARCH_URL, Uri.encode(query), 1);
    }

    @Override
    protected String overrideMangaUrl(String defaultMangaUrl) {
        String mangaId = defaultMangaUrl.substring(defaultMangaUrl.lastIndexOf("r") + 1);
        return String.format(MANGA_URL, mangaId);
    }

    @Override
    protected String overrideChapterUrl(String defaultPageUrl) {
        String id = defaultPageUrl.substring(defaultPageUrl.indexOf("#") + 1);
        return String.format(CHAPTER_URL, id);
    }

    @Override
    protected String overridePageUrl(String defaultPageUrl) {
        int start = defaultPageUrl.indexOf("#") + 1;
        int end = defaultPageUrl.indexOf("_", start);
        String id = defaultPageUrl.substring(start, end);
        return String.format(PAGE_URL, id, defaultPageUrl.substring(end+1));
    }

    @Override
    protected List<Manga> parsePopularMangasFromHtml(Document parsedHtml) {
        if (parsedHtml.text().contains("No (more) comics found!")) {
            return new ArrayList<>();
        }

        List<Manga> mangaList = new ArrayList<>();

        Elements updatedHtmlBlocks = parsedHtml.select("tr:not([id]):not([class])");
        for (Element currentHtmlBlock : updatedHtmlBlocks) {
            Manga currentlyUpdatedManga = constructMangaFromHtmlBlock(currentHtmlBlock);

            mangaList.add(currentlyUpdatedManga);
        }

        return mangaList;
    }

    @Override
    protected String parseNextPopularMangasUrl(Document parsedHtml, MangasPage page) {
        Element next = parsedHtml.select("#show_more_row").first();
        if (next == null)
            return null;

        return String.format(POPULAR_MANGAS_URL, page.page + 1);
    }

    @Override
    protected List<Manga> parseSearchFromHtml(Document parsedHtml) {
        if (parsedHtml.text().contains("No (more) comics found!")) {
            return new ArrayList<>();
        }

        List<Manga> mangaList = new ArrayList<>();

        Elements updatedHtmlBlocks = parsedHtml.select("tr:not([id]):not([class])");
        for (Element currentHtmlBlock : updatedHtmlBlocks) {
            Manga currentlyUpdatedManga = constructMangaFromHtmlBlock(currentHtmlBlock);

            mangaList.add(currentlyUpdatedManga);
        }

        return mangaList;
    }

    private Manga constructMangaFromHtmlBlock(Element htmlBlock) {
        Manga mangaFromHtmlBlock = new Manga();

        Element urlElement = htmlBlock.select("a[href^=http://bato.to]").first();
        Element updateElement = htmlBlock.select("td").get(5);

        mangaFromHtmlBlock.source = getId();

        if (urlElement != null) {
            mangaFromHtmlBlock.setUrl(urlElement.attr("href"));
            mangaFromHtmlBlock.title = urlElement.text().trim();
        }
        if (updateElement != null) {
            mangaFromHtmlBlock.last_update = parseUpdateFromElement(updateElement);
        }

        return mangaFromHtmlBlock;
    }

    @Override
    protected String parseNextSearchUrl(Document parsedHtml, MangasPage page, String query) {
        Element next = parsedHtml.select("#show_more_row").first();
        if (next == null)
            return null;

        return String.format(SEARCH_URL, query, page.page + 1);
    }

    private long parseUpdateFromElement(Element updateElement) {
        String updatedDateAsString = updateElement.text();

        try {
            Date specificDate = new SimpleDateFormat("dd MMMMM yyyy - hh:mm a", Locale.ENGLISH).parse(updatedDateAsString);

            return specificDate.getTime();
        } catch (ParseException e) {
            // Do Nothing.
        }

        return 0;
    }

    @Override
    protected Manga parseHtmlToManga(String mangaUrl, String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        Elements artistElements = parsedDocument.select("a[href^=http://bato.to/search?artist_name]");
        Element descriptionElement = parsedDocument.select("tr").get(5);
        Elements genreElements = parsedDocument.select("img[src=http://bato.to/forums/public/style_images/master/bullet_black.png]");
        Element thumbnailUrlElement = parsedDocument.select("img[src^=http://img.bato.to/forums/uploads/]").first();

        Manga newManga = new Manga();
        newManga.url = mangaUrl;

        if (artistElements != null) {
            newManga.author = artistElements.get(0).text();
            if (artistElements.size() > 1) {
                newManga.artist = artistElements.get(1).text();
            } else {
                newManga.artist = newManga.author;
            }
        }
        if (descriptionElement != null) {
            newManga.description = descriptionElement.text().substring("Description:".length()).trim();
        }
        if (genreElements != null) {
            String fieldGenres = "";
            for (int index = 0; index < genreElements.size(); index++) {
                String currentGenre = genreElements.get(index).attr("alt");

                if (index < genreElements.size() - 1) {
                    fieldGenres += currentGenre + ", ";
                } else {
                    fieldGenres += currentGenre;
                }
            }
            newManga.genre = fieldGenres;
        }
        if (thumbnailUrlElement != null) {
            newManga.thumbnail_url = thumbnailUrlElement.attr("src");
        }

        boolean fieldCompleted = unparsedHtml.contains("<td>Complete</td>");
        //TODO fix
        newManga.status = fieldCompleted + "";

        newManga.initialized = true;

        return newManga;
    }

    @Override
    protected List<Chapter> parseHtmlToChapters(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<Chapter> chapterList = new ArrayList<>();

        Elements chapterElements = parsedDocument.select("tr.row.lang_English.chapter_row");
        for (Element chapterElement : chapterElements) {
            Chapter currentChapter = constructChapterFromHtmlBlock(chapterElement);
            chapterList.add(currentChapter);
        }

        //saveChaptersToDatabase(chapterList, mangaUrl);

        return chapterList;

    }

    private Chapter constructChapterFromHtmlBlock(Element chapterElement) {
        Chapter newChapter = Chapter.create();

        Element urlElement = chapterElement.select("a[href^=http://bato.to/reader").first();
        Element dateElement = chapterElement.select("td").get(4);

        if (urlElement != null) {
            String fieldUrl = urlElement.attr("href");
            newChapter.setUrl(fieldUrl);
            newChapter.name = urlElement.text().trim();

        }
        if (dateElement != null) {
            newChapter.date_upload = parseDateFromElement(dateElement);
        }
        newChapter.date_fetch = new Date().getTime();

        return newChapter;
    }

    private long parseDateFromElement(Element dateElement) {
        String dateAsString = dateElement.text();

        Date date;
        try {
            date = new SimpleDateFormat("dd MMMMM yyyy - hh:mm a", Locale.ENGLISH).parse(dateAsString);
        } catch (ParseException e) {
            Matcher m = datePattern.matcher(dateAsString);

            if (m.matches()) {
                String number = m.group(1);
                int amount = number.contains("A") ? 1 : Integer.parseInt(m.group(1));
                String unit = m.group(2);

                Calendar cal = Calendar.getInstance();
                // Not an error
                cal.add(dateFields.get(unit), -amount);
                date = cal.getTime();
            } else {
                return 0;
            }
        }
        return date.getTime();
    }

    @Override
    protected List<String> parseHtmlToPageUrls(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<String> pageUrlList = new ArrayList<>();

        Element selectElement = parsedDocument.select("#page_select").first();

        if (selectElement != null) {
            for (Element pageUrlElement : selectElement.select("option")) {
                pageUrlList.add(pageUrlElement.attr("value"));
            }
        } else {
            // For webtoons in one page
            for (int i = 0; i < parsedDocument.select("div > img").size(); i++) {
                pageUrlList.add("");
            }
        }

        return pageUrlList;
    }

    @Override
    protected List<Page> getFirstImageFromPageUrls(List<String> pageUrls, String unparsedHtml) {
        List<Page> pages = convertToPages(pageUrls);
        if (!unparsedHtml.contains("Want to see this chapter per page instead?")) {
            String firstImage = parseHtmlToImageUrl(unparsedHtml);
            pages.get(0).setImageUrl(firstImage);
        } else {
            // For webtoons in one page
            Document parsedDocument = Jsoup.parse(unparsedHtml);
            Elements imageUrls = parsedDocument.select("div > img");
            for (int i = 0; i < pages.size(); i++) {
                pages.get(i).setImageUrl(imageUrls.get(i).attr("src"));
            }
        }
        return pages;
    }

    @Override
    protected String parseHtmlToImageUrl(String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<img id=\"comic_page\"");
        int endIndex = unparsedHtml.indexOf("</a>", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        Element imageElement = parsedDocument.getElementById("comic_page");

        return imageElement.attr("src");
    }

    @Override
    public Observable<Boolean> login(String username, String password) {
        return networkService.getStringResponse(LOGIN_URL, requestHeaders, null)
                .flatMap(response -> doLogin(response, username, password))
                .map(this::isAuthenticationSuccessful);
    }

    private Observable<Response> doLogin(String response, String username, String password) {
        Document doc = Jsoup.parse(response);
        Element form = doc.select("#login").first();
        String postUrl = form.attr("action");

        FormEncodingBuilder formBody = new FormEncodingBuilder();
        Element authKey = form.select("input[name=auth_key").first();

        formBody.add(authKey.attr("name"), authKey.attr("value"));
        formBody.add("ips_username", username);
        formBody.add("ips_password", password);
        formBody.add("invisible", "1");
        formBody.add("rememberMe", "1");

        return networkService.postData(postUrl, formBody.build(), requestHeaders);
    }

    @Override
    protected boolean isAuthenticationSuccessful(Response response) {
        return response.priorResponse() != null && response.priorResponse().code() == 302;
    }

    @Override
    public boolean isLogged() {
        try {
            for ( HttpCookie cookie : networkService.getCookies().get(new URI(BASE_URL)) ) {
                if (cookie.getName().equals("pass_hash"))
                    return true;
            }

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public Observable<List<Chapter>> pullChaptersFromNetwork(String mangaUrl) {
        Observable<List<Chapter>> observable;
        if (!isLogged()) {
            observable = login(prefs.getSourceUsername(this), prefs.getSourcePassword(this))
                    .flatMap(result -> super.pullChaptersFromNetwork(mangaUrl));
        }
        else {
            observable = super.pullChaptersFromNetwork(mangaUrl);
        }
        return observable;
    }

}
