package eu.kanade.mangafeed.sources;

import android.content.Context;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.base.Source;
import rx.Observable;

public class Batoto extends Source {

    public static final String NAME = "Batoto (EN)";
    public static final String BASE_URL = "http://bato.to";
    public static final String INITIAL_UPDATE_URL =
            "http://bato.to/search_ajax?order_cond=views&order=desc&p=";
    public static final String INITIAL_SEARCH_URL = "http://bato.to/search_ajax?";
    public static final String INITIAL_PAGE_URL = "http://bato.to/areader?";
    public static final String LOGIN_URL =
            "https://bato.to/forums/index.php?app=core&module=global&section=login";


    public Batoto(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return NAME;
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
    public int getSourceId() {
        return SourceManager.BATOTO;
    }

    @Override
    public boolean isLoginRequired() {
        return true;
    }

    @Override
    protected String getUrlFromPageNumber(int page) {
        return INITIAL_UPDATE_URL + page;
    }

    @Override
    protected String getSearchUrl(String query, int page) {
        return INITIAL_SEARCH_URL + "name=" + query + "&p=" + page;
    }

    @Override
    protected String overrideMangaUrl(String defaultMangaUrl) {
        String mangaId = defaultMangaUrl.substring(defaultMangaUrl.lastIndexOf("r") + 1);
        return "http://bato.to/comic_pop?id=" + mangaId;
    }

    @Override
    protected String overrideChapterPageUrl(String defaultPageUrl) {
        String id = defaultPageUrl.substring(defaultPageUrl.indexOf("#") + 1);
        return INITIAL_PAGE_URL + "id=" + id + "&p=1";
    }

    @Override
    protected String overrideRemainingPagesUrl(String defaultPageUrl) {
        int start = defaultPageUrl.indexOf("#") + 1;
        int end = defaultPageUrl.indexOf("_", start);
        String id = defaultPageUrl.substring(start, end);
        return INITIAL_PAGE_URL + "id=" + id + "&p=" + defaultPageUrl.substring(end+1);
    }

    private List<Manga> parseMangasFromHtml(String unparsedHtml) {
        if (unparsedHtml.contains("No (more) comics found!")) {
            return new ArrayList<>();
        }

        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<Manga> updatedMangaList = new ArrayList<>();

        Elements updatedHtmlBlocks = parsedDocument.select("tr:not([id]):not([class])");
        for (Element currentHtmlBlock : updatedHtmlBlocks) {
            Manga currentlyUpdatedManga = constructMangaFromHtmlBlock(currentHtmlBlock);

            updatedMangaList.add(currentlyUpdatedManga);
        }

        return updatedMangaList;
    }

    @Override
    public List<Manga> parsePopularMangasFromHtml(String unparsedHtml) {
        return parseMangasFromHtml(unparsedHtml);
    }

    @Override
    protected List<Manga> parseSearchFromHtml(String unparsedHtml) {
        return parseMangasFromHtml(unparsedHtml);
    }

    private Manga constructMangaFromHtmlBlock(Element htmlBlock) {
        Manga mangaFromHtmlBlock = new Manga();

        Element urlElement = htmlBlock.select("a[href^=http://bato.to]").first();
        Element nameElement = urlElement;
        Element updateElement = htmlBlock.select("td").get(5);

        mangaFromHtmlBlock.source = getSourceId();

        if (urlElement != null) {
            String fieldUrl = urlElement.attr("href");
            mangaFromHtmlBlock.url = fieldUrl;
        }
        if (nameElement != null) {
            String fieldName = nameElement.text().trim();
            mangaFromHtmlBlock.title = fieldName;
        }
        if (updateElement != null) {
            long fieldUpdate = parseUpdateFromElement(updateElement);
            mangaFromHtmlBlock.last_update = fieldUpdate;
        }

        return mangaFromHtmlBlock;
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
            String fieldDescription = descriptionElement.text().substring("Description:".length()).trim();
            newManga.description = fieldDescription;
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
            String fieldThumbnailUrl = thumbnailUrlElement.attr("src");
            newManga.thumbnail_url = fieldThumbnailUrl;
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
        Chapter newChapter = Chapter.newChapter();

        Element urlElement = chapterElement.select("a[href^=http://bato.to/reader").first();
        Element nameElement = urlElement;
        Element dateElement = chapterElement.select("td").get(4);

        if (urlElement != null) {
            String fieldUrl = urlElement.attr("href");
            newChapter.url = fieldUrl;
        }
        if (nameElement != null) {
            String fieldName = nameElement.text().trim();
            newChapter.name = fieldName;
        }
        if (dateElement != null) {
            long fieldDate = parseDateFromElement(dateElement);
            newChapter.date_upload = fieldDate;
        }
        newChapter.date_fetch = new Date().getTime();

        return newChapter;
    }

    private long parseDateFromElement(Element dateElement) {
        String dateAsString = dateElement.text();

        try {
            Date specificDate = new SimpleDateFormat("dd MMMMM yyyy - hh:mm a", Locale.ENGLISH).parse(dateAsString);

            return specificDate.getTime();
        } catch (ParseException e) {
            // Do Nothing.
        }

        return 0;
    }

    @Override
    protected List<String> parseHtmlToPageUrls(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<String> pageUrlList = new ArrayList<>();

        Elements pageUrlElements = parsedDocument.getElementById("page_select").getElementsByTag("option");
        for (Element pageUrlElement : pageUrlElements) {
            pageUrlList.add(pageUrlElement.attr("value"));
        }

        return pageUrlList;
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
        return mNetworkService.getStringResponse(LOGIN_URL, mRequestHeaders, null)
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

        return mNetworkService.postData(postUrl, formBody.build(), mRequestHeaders);
    }

    @Override
    protected boolean isAuthenticationSuccessful(Response response) {
        return response.priorResponse() != null && response.priorResponse().code() == 302;
    }

    @Override
    public boolean isLogged() {
        try {
            for ( HttpCookie cookie : mNetworkService.getCookies().get(new URI(BASE_URL)) ) {
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

