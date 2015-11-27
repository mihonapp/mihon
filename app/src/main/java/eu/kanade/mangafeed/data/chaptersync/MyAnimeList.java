package eu.kanade.mangafeed.data.chaptersync;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.database.models.ChapterSync;
import eu.kanade.mangafeed.data.network.NetworkHelper;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import rx.Observable;

public class MyAnimeList extends BaseChapterSync {

    @Inject PreferencesHelper preferences;
    @Inject NetworkHelper networkService;

    private Headers headers;

    public static final String BASE_URL = "http://myanimelist.net";

    private static final String ENTRY = "entry";
    private static final String CHAPTER = "chapter";

    public MyAnimeList(Context context) {
        App.get(context).getComponent().inject(this);

        String username = preferences.getChapterSyncUsername(this);
        String password = preferences.getChapterSyncPassword(this);

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
        return ChapterSyncManager.MYANIMELIST;
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
        return !preferences.getChapterSyncUsername(this).isEmpty()
                && !preferences.getChapterSyncPassword(this).isEmpty();
    }

    public String getSearchUrl(String query) {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/manga/search.xml")
                .appendQueryParameter("q", query)
                .toString();
    }

    public Observable<List<ChapterSync>> search(String query) {
        return networkService.getStringResponse(getSearchUrl(query), headers, null)
                .map(Jsoup::parse)
                .flatMap(doc -> Observable.from(doc.select("entry")))
                .map(entry -> {
                    ChapterSync chapter = ChapterSync.create(this);
                    chapter.title = entry.select("title").first().text();
                    chapter.remote_id = Integer.parseInt(entry.select("id").first().text());
                    return chapter;
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

    public Observable<List<ChapterSync>> getList(String username) {
        return networkService.getStringResponse(getListUrl(username), headers, null)
                .map(Jsoup::parse)
                .flatMap(doc -> Observable.from(doc.select("manga")))
                .map(entry -> {
                    ChapterSync chapter = ChapterSync.create(this);
                    chapter.title = entry.select("series_title").first().text();
                    chapter.remote_id = Integer.parseInt(
                            entry.select("series_mangadb_id").first().text());
                    chapter.last_chapter_read = Integer.parseInt(
                            entry.select("my_read_chapters").first().text());
                    return chapter;
                })
                .toList();
    }

    public String getUpdateUrl(ChapterSync chapter) {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/mangalist/update")
                .appendPath(chapter.remote_id + ".xml")
                .toString();
    }

    public Observable<Response> update(ChapterSync chapter) {
        XmlSerializer xml = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        try {
            xml.setOutput(writer);
            xml.startDocument("UTF-8", false);
            xml.startTag("", ENTRY);
            xml.startTag("", CHAPTER);
            xml.text(chapter.last_chapter_read + "");
            xml.endTag("", CHAPTER);
            xml.endTag("", ENTRY);
            xml.endDocument();
        } catch (IOException e) {
            return Observable.error(e);
        }

        FormEncodingBuilder form = new FormEncodingBuilder();
        form.add("data", writer.toString());

        return networkService.postData(getUpdateUrl(chapter), form.build(), headers);
    }

    public void createHeaders(String username, String password) {
        Headers.Builder builder = new Headers.Builder();
        builder.add("Authorization", Credentials.basic(username, password));
//        builder.add("User-Agent", "");
        setHeaders(builder.build());
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

}
