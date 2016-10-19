package eu.kanade.tachiyomi.data.source.online.english;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ShareCompat;
import android.text.TextUtils;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.network.RequestsKt;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.source.Language;
import eu.kanade.tachiyomi.data.source.LanguageKt;
import eu.kanade.tachiyomi.data.source.model.MangasPage;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.data.source.online.OnlineSource;
import exh.DialogLogin;
import exh.NetworkManager;
import exh.StringJoiner;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class EHentai extends OnlineSource {

    public static final String[] GENRE_LIST = {
            "doujinshi",
            "manga",
            "artistcg",
            "gamecg",
            "western",
            "non-h",
            "imageset",
            "cosplay",
            "asianporn",
            "misc"
    };

    public static final String QUERY_PREFIX = "?f_apply=Apply+Filter";

    public static String HOST = "http://g.e-hentai.org/";
    public static String RAW_EXHHOST = "exhentai.org/";
    public static String EXHHOST = "http://" + RAW_EXHHOST;

    private static final String QUALITY_PLACEHOLDER = "{QUALITY}";
    private static final String DEFAULT_CONFIG = "uconfig=uh_y-lt_m-tl_r-tr_2-ts_m-prn_y-dm_l-ar_0-xns_0-rc_0-rx_0-ry_0-cs_a-fs_p-to_a-pn_0-sc_0-ru_rrggb-xr_" + QUALITY_PLACEHOLDER + "-sa_y-oi_n-qb_n-tf_n-hh_-hp_-hk_-cats_0-xl_-ms_n-mt_n;";

    public static final String FAVORITES_PATH = "favorites.php";

    private boolean isExhentai = false;
    private Context context;
    private int id;

    private PreferencesHelper helper;

    public EHentai(Context context, int id, boolean isExhentai) {
        super();
        this.context = context.getApplicationContext();
        this.isExhentai = isExhentai;
        helper = new PreferencesHelper(context);
        this.id = id;
//        requestHeaders = headersBuilder().build();
//        glideHeaders = glideHeadersBuilder().build();
    }

    private static boolean isGenreEnabled(String genre, List<Filter> filters) {
        for(Filter filter : filters) {
            if(filter.getId().equals(genre)) {
                return true;
            }
        }
        return false;
    }

    private static String buildGenreString(List<Filter> filters) {
        StringBuilder genreString = new StringBuilder();
        for (String genre : GENRE_LIST) {
            genreString.append("&f_");
            genreString.append(genre);
            genreString.append("=");
            genreString.append(filters.isEmpty() || isGenreEnabled(genre, filters) ? "1" : "0");
        }
        return genreString.toString();
    }

    private static String getQualityMode(PreferencesHelper prefHelper) {
        return prefHelper.getPrefs().getString("ehentai_quality", "auto");
    }

    @NonNull
    @Override public Language getLang() {
        return LanguageKt.getALL();
    }

    @NonNull
    @Override public String getName() {
        return isExhentai ? "ExHentai" : "EHentai";
    }

    @NonNull
    @Override public String getBaseUrl() {
        if(isExhentai) {
            return buildExhHost(helper.getPrefs());
        } else {
            return HOST;
        }
    }

    public static String buildExhHost(SharedPreferences preferences) {
        boolean secureExh = preferences.getBoolean("secure_exh", true);
        if (secureExh) {
            return "https://" + RAW_EXHHOST;
        } else {
            return "http://" + RAW_EXHHOST;
        }
    }

    @NonNull
    @Override protected String popularMangaInitialUrl() {
        return getBaseUrl() + QUERY_PREFIX + buildGenreString(Collections.<Filter>emptyList());
    }

    @NotNull
    @Override protected String searchMangaInitialUrl(@NotNull String query, @NotNull List<Filter> filters) {
        try {
            log("Query: " + getBaseUrl() + QUERY_PREFIX + buildGenreString(filters) + "&f_search=" + URLEncoder.encode(query, "UTF-8"));
            return getBaseUrl() + QUERY_PREFIX + buildGenreString(filters) + "&f_search=" + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            //How can this happen :/
            throw new RuntimeException(e);
        }
    }

    //Yes OkHttp has an interceptor but the glide headers still require the cookie strings
    @NonNull
    @Override protected Headers.Builder headersBuilder() {
        return getHeadersBuilder(helper);
    }

    public static Headers.Builder getHeadersBuilder(PreferencesHelper helper) {
        Headers.Builder builder = new Headers.Builder();
        String cookies = appendQualityChar(helper, helper.getPrefs().getString("eh_cookie_string", "").trim());
        cookies = cleanCookieString("nw=1; " + cookies);
        log("New cookies: " + cookies);
        builder.add("Cookie", cookies);
        return builder;
    }

    /*@Override protected LazyHeaders.Builder glideHeadersBuilder() {
        LazyHeaders.Builder builder = super.glideHeadersBuilder();
        builder.addHeader("Cookie", helper.getPrefs().getString("eh_cookie_string", "").trim());
        return builder;
    }*/

    public static void exportMangaURLs(Activity activity, List<Manga> mangaList) {
        StringJoiner urlJoiner = new StringJoiner("\n");
        for (Manga manga : mangaList) {
            if (!TextUtils.isEmpty(manga.getUrl())) {
                String url = manga.getUrl();
                if (manga.getSource() == 1) {
                    url = HOST + url;
                } else if (manga.getSource() == 2) {
                    url = EXHHOST + url;
                }
                urlJoiner.add(url);
            }
        }
        ShareCompat.IntentBuilder
                .from(activity) // getActivity() or activity field if within Fragment
                .setText(urlJoiner.toString())
                .setType("text/plain") // most general text sharing MIME type
                .setChooserTitle("Share Gallery URLs")
                .startChooser();
    }

    @Override
    public boolean getSupportsLatest() {
        return true;
    }

    @Override
    protected void searchMangaParse(@NotNull Response response, @NotNull MangasPage page, @NotNull String query, @NotNull List<Filter> filters) {
        popularMangaParse(response, page);
    }

    @NotNull
    @Override
    protected String latestUpdatesInitialUrl() {
        //TODO Change this when we actually parse the popular stuff!
        return popularMangaInitialUrl();
    }

    @Override
    protected void latestUpdatesParse(@NotNull Response response, @NotNull MangasPage page) {
        popularMangaParse(response, page);
    }

    public static class FavoritesResponse {
        public Map<String, List<Manga>> favs;
        public List<String> favCategories;

        public FavoritesResponse(Map<String, List<Manga>> favs, List<String> favCategories) {
            this.favs = favs;
            this.favCategories = favCategories;
        }
    }

    public static class BuildFavoritesBaseResponse {
        public final String favoritesBase;
        public final int id;

        public BuildFavoritesBaseResponse(String favoritesBase, int id) {
            this.favoritesBase = favoritesBase;
            this.id = id;
        }
    }

    private  static BuildFavoritesBaseResponse buildFavoritesBase(Context context, SharedPreferences preferences) {
        String favoritesBase;
        int id;
        if(DialogLogin.isLoggedIn(context, false)) {
            favoritesBase = buildExhHost(preferences);
            id = 2;
        } else {
            favoritesBase = HOST;
            id = 1;
        }
        favoritesBase += FAVORITES_PATH;
        return new BuildFavoritesBaseResponse(favoritesBase, id);
    }

    public static FavoritesResponse fetchFavorites(Context context) throws IOException {
        PreferencesHelper helper = new PreferencesHelper(context);
        BuildFavoritesBaseResponse buildFavoritesBaseResponse = buildFavoritesBase(context, helper.getPrefs());
        String favoritesBase = buildFavoritesBaseResponse.favoritesBase;
        int id = buildFavoritesBaseResponse.id;
        //Used to get "s" cookie
        Response response1 = NetworkManager.getInstance().getClient().newCall(
                RequestsKt.GET(favoritesBase, getHeadersBuilder(helper).build(), RequestsKt.getDEFAULT_CACHE_CONTROL())).execute();
        //Extract favorite names
        List<String> favNames = new ArrayList<>();
        Document onlyFavsDoc = responseToDocument(response1);
        for(Element element : onlyFavsDoc.select(".nosel").first().children()) {
            if(element.children().size() > 0) {
                favNames.add(element.child(2).text());
            }
        }
        String sCookie = null;
        Map<String, String> foundCookies = getCookies(response1.header("Set-Cookie"));
        if(foundCookies != null) {
            sCookie = foundCookies.get("s");
        }
        Headers.Builder cookiesBuilder = getHeadersBuilder(helper);
        String oldCookies;
        if((oldCookies = cookiesBuilder.get("Cookie")) != null && sCookie != null) {
            cookiesBuilder.removeAll("Cookie");
            cookiesBuilder.add("Cookie", "s=" + sCookie + "; " + oldCookies);
        }
        Response response2 = NetworkManager.getInstance().getClient().newCall(
                RequestsKt.GET(favoritesBase, cookiesBuilder.build(), RequestsKt.getDEFAULT_CACHE_CONTROL())).execute();
        ParsedMangaPage parsed = parseMangaPage(response2, id);
        return new FavoritesResponse(parsed.mangas, favNames);
    }

    private static class ParsedMangaPage {
        public String nextPageUrl;
        public Map<String, List<Manga>> mangas;
    }

    private static ParsedMangaPage parseMangaPage(Response response, int id) {
        ParsedMangaPage mangaPage = new ParsedMangaPage();
        Map<String, List<Manga>> mangas = new HashMap<>();
        mangaPage.mangas = mangas;
        Document parsedHtml = responseToDocument(response);
        for (Element element : parsedHtml.select("div[style=position:relative]")) {
            Element info = element.select("div.it5").first().children().first();
            //Append no warning query
            Manga manga = Manga.Companion.create(pathOnly(info.attr("href")), id);
            manga.setTitle(info.text());
            Element pic = element.select("div.it2").first();
            if (pic.children().first() != null) {
                manga.setThumbnail_url(pic.children().first().attr("src"));
            } else {
                //Thumbnails are encoded
                String[] split = pic.text().split("~");
                manga.setThumbnail_url("http://" + split[1] + "/" + split[2]);
            }
            String favoriteName = "Default";
            Element parent = element.select("div.it3").first();
            if(parent != null) {
                for(Element possibleFavoriteElement : parent.children()) {
                    if(possibleFavoriteElement.id().startsWith("favicon")) {
                        favoriteName = possibleFavoriteElement.attr("title");
                        break;
                    }
                }
            }
            List<Manga> mangaList = mangas.get(favoriteName);
            if(mangaList == null) {
                mangaList = new ArrayList<>();
                mangas.put(favoriteName, mangaList);
            }
            mangaList.add(manga);
        }
        mangaPage.nextPageUrl = parseNextSearchUrl(parsedHtml);
        return mangaPage;
    }

    private static Document responseToDocument(Response response) {
        try {
            return Jsoup.parse(response.body().string(), response.request().url().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override protected void popularMangaParse(@NonNull Response response, @NonNull MangasPage page) {
        if (isExhentai && !loginIfEHentai()) {
            return;
        }
        ParsedMangaPage parsedPage = parseMangaPage(response, getId());
        for(List<Manga> found : parsedPage.mangas.values()) {
            for(Manga manga : found) {
                page.getMangas().add(manga);
            }
        }
        page.setNextPageUrl(parsedPage.nextPageUrl);
    }

    public static String pathOnly(String url) {
        return pathOnly(url, true);
    }

    public static String pathOnly(String url, boolean appendNw) {
        if(url == null) {
            return null;
        }
        try {
            URL urlObj = new URL(url);
            StringBuilder builder = new StringBuilder(urlObj.getPath());
            if (urlObj.getQuery() != null) {
                builder.append('?');
                builder.append(urlObj.getQuery());
                if(appendNw && !urlObj.getQuery().trim().isEmpty()) {
                    builder.append("&");
                }
            } else if(appendNw) {
                builder.append("?");
            }
            if(appendNw) {
                builder.append("nw=always");
            }
            String string = builder.toString();
            while(string.startsWith("/")) {
                string = string.substring(1);
            }
            return string;
        } catch (MalformedURLException e) {
            return url;
        }
    }

    protected static String parseNextSearchUrl(Document parsedHtml) {
        Elements buttons = parsedHtml.select("a[onclick=return false]");
        Element lastButton = buttons.last();
        if (lastButton != null) {
            if (lastButton.text().equals(">")) {
                return buttons.last().attr("href");
            }
        }
        return null;
    }

    @Override
    protected void mangaDetailsParse(@NotNull Response response, @NotNull Manga m) {
        Document document = responseToDocument(response);
        m.setUrl(pathOnly(response.request().url().toString()));
        log(pathOnly(response.request().url().toString()));
        m.setSource(getId());
        StringBuilder synopsis = new StringBuilder();
        String title = "";
        try {
            title = document.select("#gn").text();
            synopsis.append("Title: ");
            synopsis.append(title);
            try {
                Elements jpTitleElements = document.select("h1[id=gj]");
                if(jpTitleElements.size() > 0) {
                    synopsis.append("\n");
                    synopsis.append("Japanese Title: ");
                    synopsis.append(jpTitleElements.text());
                }
            } catch (Exception ignored) {
            }
            synopsis.append("\n\n");
        } catch (Exception ignored) {
        }
        m.setTitle(title);
        //Synopsis
        try {
            StringBuilder tempBuilder = new StringBuilder();
            for (Element element : document.select("div[id=gdd]").first().children().first().children().first().children()) {
                tempBuilder.append(element.text()).append("\n");
            }
            synopsis.append(tempBuilder);
        } catch (Exception e) {
            synopsis.append("Error fetching description!");
        }
        //Ratings
        try {
            String ratingString = document.select("td[id=rating_label]").first().text();
            String ratingCount = document.select("span[id=rating_count]").first().text().trim();
            ratingString = ratingString.split(": ")[1].trim();
            synopsis.append("Rating: ").append(ratingString).append(" (").append(ratingCount).append(")\n");
        } catch (Exception ignored) {
        }
        synopsis.append("\nTags:\n");
        try {
            StringBuilder tempBuilder = new StringBuilder();
            Element tbody = document.select("div[id=taglist]").first().children().first().children().first();
            for (Element element : tbody.children()) {
                String name = element.child(0).text();
                Elements tags = element.select("a");
                StringBuilder tagBuilder = new StringBuilder();
                for (Element tag : tags) {
                    tagBuilder.append(" <");
                    tagBuilder.append(tag.text());
                    tagBuilder.append(">");
                }
                tempBuilder.append("▪ ");
                tempBuilder.append(name);
                tempBuilder.append(tagBuilder);
                tempBuilder.append('\n');
            }
            synopsis.append(tempBuilder);
        } catch (Exception e) {
            synopsis.append("No tags have been added for this gallery yet.");
        }
        m.setDescription(synopsis.toString());
        //Image
        try {
            m.setThumbnail_url(document.select("div[id=gd1]").first().children().first().attr("src"));
        } catch (Exception ignored) {
        }
        //Author
        try {
            m.setAuthor(document.select("div[id=gdn]").first().children().first().text());
        } catch (Exception e) {
            synopsis.append("Error fetching author!");
        }
        //Genre
        try {
            m.setGenre(document.select("img[class=ic]").first().attr("alt"));
        } catch (Exception e) {
            synopsis.append("Error fetching genre!");
        }
    }

    @NotNull
    @Override
    protected Request popularMangaRequest(@NotNull MangasPage page) {
        if (isExhentai && !loginIfEHentai()) {
            page.getMangas().clear();
            page.setNextPageUrl(null);
            return super.popularMangaRequest(page);
        }
        return super.popularMangaRequest(page);
    }

    @Override
    protected void chapterListParse(@NotNull Response response, @NotNull List<Chapter> chapters) {
        //Chapters
        Chapter mainChapter = Chapter.Companion.create();
        mainChapter.setUrl(pathOnly(response.request().url().toString()));
        mainChapter.setName("Chapter");
        chapters.add(mainChapter);
    }

    boolean loginIfEHentai() {
        Handler uiHandler = new Handler(Looper.getMainLooper());
        final boolean isLoggedIn = DialogLogin.isLoggedIn(context, false);
        if (!isLoggedIn) {
            uiHandler.post(new Runnable() {
                @Override public void run() {
                    Toast.makeText(context, "In order to access ExHentai you must be logged in! Please log in the settings section!", Toast.LENGTH_SHORT).show();
                }
            });
            return false;
        }
        return true;
    }

    private String parseChapterPage(ArrayList<String> urls, String url) throws Exception {
        log("Parsing chapter page: " + url);
        String source = getClient().newCall(RequestsKt.GET(getBaseUrl() + url, getHeaders(), RequestsKt.getDEFAULT_CACHE_CONTROL()))
                .execute().body().string();
        Document document = Jsoup.parse(source, url);
        //Parse each page
        for (Element element : document.select("div[class=gdtm]")) {
            Element next = element.children().first().children().first();
            String pageUrl = next.attr("href");
            int pageNumber = Integer.parseInt(next.children().first().attr("alt"));
            log("Got page: " + pageNumber + ", " + pageUrl);
            urls.add(pageUrl);
        }

        //Parse to get next page
        Elements selection = document.select("a[onclick=return false]");
        if (selection.size() < 1) {
            return null;
        } else {
            if (selection.last().text().equals(">")) {
                return pathOnly(selection.last().attr("href"), false);
            } else {
                return null;
            }
        }
    }

    @Override
    protected void pageListParse(@NotNull Response response, @NotNull List<Page> pages) {
        ArrayList<String> urls = new ArrayList<>();
        response.body().close();
        String url = pathOnly(response.request().url().toString()); //Have to do this as EXH chapters span multiple pages
        while (url != null) {
            try {
                url = parseChapterPage(urls, url);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for(int i = 0; i < urls.size(); i++) {
            pages.add(new Page(i, urls.get(i), null, null));
        }
    }

    public static void performLogout(Context context) {
        log("Logging out...");
        NetworkManager.getInstance().getCookieManager().getCookieStore().removeAll();
        android.webkit.CookieManager.getInstance().removeAllCookie();
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove("eh_cookie_string").apply();
    }

    @NotNull
    @Override
    protected String imageUrlParse(@NotNull Response response) {
        Document document = responseToDocument(response);

        if (isExhentai) {
            Element element = document.select("#img").first();
            if (element != null) {
                log("Image URL: " + element.attr("src"));
                return element.attr("src");
            }
            log("NO IMAGE FOUND!");
        } else {
            for (Element element : document.select("div[class=sni] img")) {
                if (!element.attr("src").contains("http://ehgt.org/")) {
                    log("Image URL: " + element.attr("src"));
                    return element.attr("src");
                }
            }
            log("NO IMAGE FOUND!");
        }
        return "";
    }

    private static String appendQualityChar(PreferencesHelper helper, String string) {
        String qualityChar = "a";
        switch (getQualityMode(helper)) {
            case "auto":
                qualityChar = "a";
                break;
            case "ovrs_2400":
                qualityChar = "2400";
                break;
            case "ovrs_1600":
                qualityChar = "1600";
                break;
            case "high":
                qualityChar = "1280";
                break;
            case "med":
                qualityChar = "980";
                break;
            case "low":
                qualityChar = "780";
                break;
        }
        if(!string.endsWith(";") && !string.isEmpty())
            string += ";";
        if(!string.endsWith(" ") && !string.isEmpty())
            string += " ";
        return string + DEFAULT_CONFIG.replace(QUALITY_PLACEHOLDER, qualityChar);
    }

    public static Interceptor buildInterceptor(Context context) {
        final PreferencesHelper localPreferenceHelper = new PreferencesHelper(context);
        return new Interceptor() {
            @Override public Response intercept(Chain chain) throws IOException {
                Request originalRequest = chain.request();
                String originalCookies;
                if (originalRequest.header("Cookie") != null) {
                    originalCookies = originalRequest.header("Cookie").trim();
                } else {
                    originalCookies = "";
                }
                String newCookies = appendQualityChar(localPreferenceHelper, localPreferenceHelper.getPrefs().getString("eh_cookie_string", "").trim()) + " " + originalCookies;
                newCookies = cleanCookieString("nw=1; " + newCookies); //No warning
                Request requestWithUserAgent = originalRequest.newBuilder()
                        .removeHeader("Cookie")
                        .addHeader("Cookie", newCookies)
                        .removeHeader("User-Agent")
                        .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.94 Safari/537.36")
                        .build();
                log("NewCookies: " + newCookies);
                return chain.proceed(requestWithUserAgent);
            }
        };
    }

    //Removes duplicate entries in cookie string and reformats it
    public static String cleanCookieString(String cookies) {
        Map<String, String> foundCookies = getCookies(cookies);
        if(foundCookies == null) {
            return cookies;
        }
        StringJoiner cookieJoiner = new StringJoiner("; ");
        for(Map.Entry<String, String> cookie : foundCookies.entrySet()) {
            cookieJoiner.add(cookie.getKey() + "=" + cookie.getValue());
        }
        return cookieJoiner.toString();
    }

    public static Map<String, String> getCookies(String cookies) {
        Map<String, String> foundCookies = new HashMap<>();
        for(String cookie : cookies.split(";")) {
            String[] splitCookie = cookie.split("=");
            if(splitCookie.length < 2) {
                log("Invalid cookie string!");
                return null;
            }
            String trimmedKey = splitCookie[0].trim();
            if(!foundCookies.containsKey(trimmedKey)) {
                foundCookies.put(trimmedKey, splitCookie[1].trim());
            }
        }
        return foundCookies;
    }

    private static List<Filter> filterList = createFilterList();

    private static List<Filter> createFilterList() {
        List<Filter> filters = new ArrayList<>();
        for(String genre : GENRE_LIST) {
            filters.add(new Filter(genre, genre));
        }
        return filters;
    }

    @NotNull
    @Override
    public List<Filter> getFilterList() {
        return filterList;
    }

    private static void log(String string) {
//        Util.d("EHentai", string);
    }

    @Override
    public int getId() {
        return id;
    }
}
