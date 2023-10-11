package eu.kanade.tachiyomi.data.track.suwayomi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.nio.charset.Charset
import java.security.MessageDigest

class SuwayomiApi(private val trackId: Long) {

    private val network: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private val client: OkHttpClient =
        network.client.newBuilder()
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
            .build()

    private fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        if (basePassword.isNotEmpty() && baseLogin.isNotEmpty()) {
            val credentials = Credentials.basic(baseLogin, basePassword)
            add("Authorization", credentials)
        }
    }

    private val headers: Headers by lazy { headersBuilder().build() }

    private val baseUrl by lazy { getPrefBaseUrl() }
    private val baseLogin by lazy { getPrefBaseLogin() }
    private val basePassword by lazy { getPrefBasePassword() }

    suspend fun getTrackSearch(trackUrl: String): TrackSearch = withIOContext {
        val url = try {
            // test if getting api url or manga id
            val mangaId = trackUrl.toLong()
            "$baseUrl/api/v1/manga/$mangaId"
        } catch (e: NumberFormatException) {
            trackUrl
        }

        val manga = with(json) {
            client.newCall(GET("$url/full", headers))
                .awaitSuccess()
                .parseAs<MangaDataClass>()
        }

        TrackSearch.create(trackId).apply {
            title = manga.title
            cover_url = "$url/thumbnail"
            summary = manga.description.orEmpty()
            tracking_url = url
            total_chapters = manga.chapterCount.toInt()
            publishing_status = manga.status
            last_chapter_read = manga.lastChapterRead?.chapterNumber ?: 0F
            status = when (manga.unreadCount) {
                manga.chapterCount -> Suwayomi.UNREAD
                0L -> Suwayomi.COMPLETED
                else -> Suwayomi.READING
            }
        }
    }

    suspend fun updateProgress(track: Track): Track {
        val url = track.tracking_url
        val chapters = with(json) {
            client.newCall(GET("$url/chapters", headers))
                .awaitSuccess()
                .parseAs<List<ChapterDataClass>>()
        }
        val lastChapterIndex = chapters.first { it.chapterNumber == track.last_chapter_read }.index

        client.newCall(
            PUT(
                "$url/chapter/$lastChapterIndex",
                headers,
                FormBody.Builder(Charset.forName("utf8"))
                    .add("markPrevRead", "true")
                    .add("read", "true")
                    .build(),
            ),
        ).awaitSuccess()

        return getTrackSearch(track.tracking_url)
    }

    private val sourceId by lazy {
        val key = "tachidesk/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$sourceId", Context.MODE_PRIVATE)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
    private fun getPrefBaseLogin(): String = preferences.getString(LOGIN_TITLE, LOGIN_DEFAULT)!!
    private fun getPrefBasePassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!
}

private const val ADDRESS_TITLE = "Server URL Address"
private const val ADDRESS_DEFAULT = ""
private const val LOGIN_TITLE = "Login (Basic Auth)"
private const val LOGIN_DEFAULT = ""
private const val PASSWORD_TITLE = "Password (Basic Auth)"
private const val PASSWORD_DEFAULT = ""
