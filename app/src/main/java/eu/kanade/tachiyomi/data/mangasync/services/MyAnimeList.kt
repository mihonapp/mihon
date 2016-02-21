package eu.kanade.tachiyomi.data.mangasync.services

import android.content.Context
import android.net.Uri
import android.util.Xml
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.MangaSync
import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService
import eu.kanade.tachiyomi.data.network.get
import eu.kanade.tachiyomi.data.network.post
import eu.kanade.tachiyomi.util.selectInt
import eu.kanade.tachiyomi.util.selectText
import okhttp3.*
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlSerializer
import rx.Observable
import java.io.StringWriter

fun XmlSerializer.inTag(tag: String, body: String, namespace: String = "") {
    startTag(namespace, tag)
    text(body)
    endTag(namespace, tag)
}

class MyAnimeList(private val context: Context, id: Int) : MangaSyncService(context, id) {

    private lateinit var headers: Headers
    private lateinit var username: String

    companion object {
        val BASE_URL = "http://myanimelist.net"

        private val ENTRY_TAG = "entry"
        private val CHAPTER_TAG = "chapter"
        private val SCORE_TAG = "score"
        private val STATUS_TAG = "status"

        val READING = 1
        val COMPLETED = 2
        val ON_HOLD = 3
        val DROPPED = 4
        val PLAN_TO_READ = 6

        val DEFAULT_STATUS = READING
        val DEFAULT_SCORE = 0
    }

    init {
        val username = preferences.getMangaSyncUsername(this)
        val password = preferences.getMangaSyncPassword(this)

        if (!username.isEmpty() && !password.isEmpty()) {
            createHeaders(username, password)
        }
    }

    override val name: String
        get() = "MyAnimeList"

    fun getLoginUrl(): String {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/account/verify_credentials.xml")
                .toString()
    }

    override fun login(username: String, password: String): Observable<Boolean> {
        createHeaders(username, password)
        return networkService.request(get(getLoginUrl(), headers))
                .map { it.code() == 200 }
    }

    fun getSearchUrl(query: String): String {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/manga/search.xml")
                .appendQueryParameter("q", query)
                .toString()
    }

    fun search(query: String): Observable<List<MangaSync>> {
        return networkService.requestBody(get(getSearchUrl(query), headers))
                .map { Jsoup.parse(it) }
                .flatMap { Observable.from(it.select("entry")) }
                .filter { it.select("type").text() != "Novel" }
                .map {
                    val manga = MangaSync.create(this)
                    manga.title = it.selectText("title")
                    manga.remote_id = it.selectInt("id")
                    manga.total_chapters = it.selectInt("chapters")
                    manga
                }
                .toList()
    }

    fun getListUrl(username: String): String {
        return Uri.parse(BASE_URL).buildUpon()
                .appendPath("malappinfo.php")
                .appendQueryParameter("u", username)
                .appendQueryParameter("status", "all")
                .appendQueryParameter("type", "manga")
                .toString()
    }

    // MAL doesn't support score with decimals
    fun getList(): Observable<List<MangaSync>> {
        return networkService.requestBody(get(getListUrl(username), headers), true)
                .map { Jsoup.parse(it) }
                .flatMap { Observable.from(it.select("manga")) }
                .map {
                    val manga = MangaSync.create(this)
                    manga.title = it.selectText("series_title")
                    manga.remote_id = it.selectInt("series_mangadb_id")
                    manga.last_chapter_read = it.selectInt("my_read_chapters")
                    manga.status = it.selectInt("my_status")
                    manga.score = it.selectInt("my_score").toFloat()
                    manga.total_chapters = it.selectInt("series_chapters")
                    manga
                }
                .toList()
    }

    fun getUpdateUrl(manga: MangaSync): String {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/mangalist/update")
                .appendPath(manga.remote_id.toString() + ".xml")
                .toString()
    }

    override fun update(manga: MangaSync): Observable<Response> {
        return Observable.defer {
            if (manga.total_chapters != 0 && manga.last_chapter_read == manga.total_chapters) {
                manga.status = COMPLETED
            }
            networkService.request(post(getUpdateUrl(manga), headers, getMangaPostPayload(manga)))
        }

    }

    fun getAddUrl(manga: MangaSync): String {
        return Uri.parse(BASE_URL).buildUpon()
                .appendEncodedPath("api/mangalist/add")
                .appendPath(manga.remote_id.toString() + ".xml")
                .toString()
    }

    override fun add(manga: MangaSync): Observable<Response> {
        return Observable.defer {
            networkService.request(post(getAddUrl(manga), headers, getMangaPostPayload(manga)))
        }
    }

    private fun getMangaPostPayload(manga: MangaSync): RequestBody {
        val xml = Xml.newSerializer()
        val writer = StringWriter()

        with(xml) {
            setOutput(writer)
            startDocument("UTF-8", false)
            startTag("", ENTRY_TAG)

            // Last chapter read
            if (manga.last_chapter_read != 0) {
                inTag(CHAPTER_TAG, manga.last_chapter_read.toString())
            }
            // Manga status in the list
            inTag(STATUS_TAG, manga.status.toString())

            // Manga score
            inTag(SCORE_TAG, manga.score.toString())

            endTag("", ENTRY_TAG)
            endDocument()
        }

        val form = FormBody.Builder()
        form.add("data", writer.toString())
        return form.build()
    }

    override fun bind(manga: MangaSync): Observable<Response> {
        return getList()
                .flatMap {
                    manga.sync_id = id
                    for (remoteManga in it) {
                        if (remoteManga.remote_id == manga.remote_id) {
                            // Manga is already in the list
                            manga.copyPersonalFrom(remoteManga)
                            return@flatMap update(manga)
                        }
                    }
                    // Set default fields if it's not found in the list
                    manga.score = DEFAULT_SCORE.toFloat()
                    manga.status = DEFAULT_STATUS
                    return@flatMap add(manga)
                }
    }

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            COMPLETED -> getString(R.string.completed)
            ON_HOLD -> getString(R.string.on_hold)
            DROPPED -> getString(R.string.dropped)
            PLAN_TO_READ -> getString(R.string.plan_to_read)
            else -> ""
        }
    }

    fun createHeaders(username: String, password: String) {
        this.username = username
        val builder = Headers.Builder()
        builder.add("Authorization", Credentials.basic(username, password))
        builder.add("User-Agent", "api-indiv-9F93C52A963974CF674325391990191C")
        headers = builder.build()
    }

}
