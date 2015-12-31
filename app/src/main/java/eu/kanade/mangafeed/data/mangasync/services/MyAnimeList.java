package eu.kanade.mangafeed.data.mangasync.services;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.MangaSync;
import eu.kanade.mangafeed.data.mangasync.MangaSyncManager;
import eu.kanade.mangafeed.data.mangasync.base.MangaSyncService;
import eu.kanade.mangafeed.data.network.NetworkHelper;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import rx.Observable;

public class MyAnimeList extends MangaSyncService {

    @Inject PreferencesHelper preferences;
    @Inject NetworkHelper networkService;

    private Headers headers;
    private String username;

    public static final String BASE_URL = "http://myanimelist.net";

    private static final String ENTRY_TAG = "entry";
    private static final String CHAPTER_TAG = "chapter";
    private static final String SCORE_TAG = "score";
    private static final String STATUS_TAG = "status";

    public static final int NOT_IN_LIST = 0;
    public static final int READING = 1;
    public static final int COMPLETED = 2;
    public static final int ON_HOLD = 3;
    public static final int DROPPED = 4;
    public static final int PLAN_TO_READ = 6;

    public static final int DEFAULT_STATUS = READING;
    public static final int DEFAULT_SCORE = 0;

    private Context context;

    public MyAnimeList(Context context) {
        this.context = context;
        App.get(context).getComponent().inject(this);

        String username = preferences.getMangaSyncUsername(this);
        String password = preferences.getMangaSyncPassword(this);

        if (!username.isEmpty() && !password.isEmpty()) {
            createHeaders(username, password);
        }
    }

    @Override
    public String getName() {
        return "MyAnimeList";
    }

    @Override
    public int getId() {
        return MangaSyncManager.MYANIMELIST;
    }

    public String getLoginUrl() {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/account/verify_credentials.xml")
                .toString();
    }

    public Observable<Boolean> login(String username, String password) {
        createHeaders(username, password);
        return networkService.getResponse(getLoginUrl(), headers, null)
                .map(response -> response.code() == 200);
    }

    @Override
    public boolean isLogged() {
        return !preferences.getMangaSyncUsername(this).isEmpty()
                && !preferences.getMangaSyncPassword(this).isEmpty();
    }

    public String getSearchUrl(String query) {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/manga/search.xml")
                .appendQueryParameter("q", query)
                .toString();
    }

    public Observable<List<MangaSync>> search(String query) {
        return networkService.getStringResponse(getSearchUrl(query), headers, null)
                .map(Jsoup::parse)
                .flatMap(doc -> Observable.from(doc.select("entry")))
                .map(entry -> {
                    MangaSync manga = MangaSync.create(this);
                    manga.title = entry.select("title").first().text();
                    manga.remote_id = Integer.parseInt(entry.select("id").first().text());
                    manga.total_chapters = Integer.parseInt(entry.select("chapters").first().text());
                    return manga;
                })
                .toList();
    }

    public String getListUrl(String username) {
        return Uri.parse(BASE_URL).buildUpon()
                .appendPath("malappinfo.php")
                .appendQueryParameter("u", username)
                .appendQueryParameter("status", "all")
                .appendQueryParameter("type", "manga")
                .toString();
    }

    public Observable<List<MangaSync>> getList(String username) {
        // TODO cache this list for a few minutes
        return networkService.getStringResponse(getListUrl(username), headers, null)
                .map(Jsoup::parse)
                .flatMap(doc -> Observable.from(doc.select("manga")))
                .map(entry -> {
                    MangaSync manga = MangaSync.create(this);
                    manga.title = entry.select("series_title").first().text();
                    manga.remote_id = Integer.parseInt(
                            entry.select("series_mangadb_id").first().text());
                    manga.last_chapter_read = Integer.parseInt(
                            entry.select("my_read_chapters").first().text());
                    manga.status = Integer.parseInt(
                            entry.select("my_status").first().text());
                    // MAL doesn't support score with decimals
                    manga.score = Integer.parseInt(
                            entry.select("my_score").first().text());
                    manga.total_chapters = Integer.parseInt(
                            entry.select("series_chapters").first().text());
                    return manga;
                })
                .toList();
    }

    public String getUpdateUrl(MangaSync manga) {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/mangalist/update")
                .appendPath(manga.remote_id + ".xml")
                .toString();
    }

    public Observable<Response> update(MangaSync manga) {
        try {
            if (manga.total_chapters != 0 && manga.last_chapter_read == manga.total_chapters) {
                manga.status = COMPLETED;
            }
            RequestBody payload = getMangaPostPayload(manga);
            return networkService.postData(getUpdateUrl(manga), payload, headers);
        } catch (IOException e) {
            return Observable.error(e);
        }
    }

    public String getAddUrl(MangaSync manga) {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/mangalist/add")
                .appendPath(manga.remote_id + ".xml")
                .toString();
    }

    public Observable<Response> add(MangaSync manga) {
        try {
            RequestBody payload = getMangaPostPayload(manga);
            return networkService.postData(getAddUrl(manga), payload, headers);
        } catch (IOException e) {
            return Observable.error(e);
        }
    }

    private RequestBody getMangaPostPayload(MangaSync manga) throws IOException {
        XmlSerializer xml = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        xml.setOutput(writer);
        xml.startDocument("UTF-8", false);
        xml.startTag("", ENTRY_TAG);

        // Last chapter read
        if (manga.last_chapter_read != 0) {
            xml.startTag("", CHAPTER_TAG);
            xml.text(manga.last_chapter_read + "");
            xml.endTag("", CHAPTER_TAG);
        }
        // Manga status in the list
        xml.startTag("", STATUS_TAG);
        xml.text(manga.status + "");
        xml.endTag("", STATUS_TAG);
        // Manga score
        xml.startTag("", SCORE_TAG);
        xml.text(manga.score + "");
        xml.endTag("", SCORE_TAG);

        xml.endTag("", ENTRY_TAG);
        xml.endDocument();

        FormEncodingBuilder form = new FormEncodingBuilder();
        form.add("data", writer.toString());
        return form.build();
    }

    public Observable<Response> bind(MangaSync manga) {
        return getList(username)
                .flatMap(list -> {
                    manga.sync_id = getId();
                    for (MangaSync remoteManga : list) {
                        if (remoteManga.remote_id == manga.remote_id) {
                            // Manga is already in the list
                            manga.score = remoteManga.score;
                            manga.status = remoteManga.status;
                            manga.last_chapter_read = remoteManga.last_chapter_read;
                            return update(manga);
                        }
                    }
                    // Set default fields if it's not found in the list
                    manga.score = DEFAULT_SCORE;
                    manga.status = DEFAULT_STATUS;
                    return add(manga);
                });
    }

    @Override
    public String getStatus(int status) {
        switch (status) {
            case READING:
                return context.getString(R.string.reading);
            case COMPLETED:
                return context.getString(R.string.completed);
            case ON_HOLD:
                return context.getString(R.string.on_hold);
            case DROPPED:
                return context.getString(R.string.dropped);
            case PLAN_TO_READ:
                return context.getString(R.string.plan_to_read);
        }
        return "";
    }

    public void createHeaders(String username, String password) {
        this.username = username;
        Headers.Builder builder = new Headers.Builder();
        builder.add("Authorization", Credentials.basic(username, password));
        builder.add("User-Agent", "api-indiv-9F93C52A963974CF674325391990191C");
        setHeaders(builder.build());
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

}
