package eu.kanade.tachiyomi.data.track.suwayomi

import android.util.Log
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.nio.charset.Charset
import java.security.MessageDigest

class SuwayomiApi(private val trackId: Long) {

    private val network: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private inner class OkAuthorizationInterceptor(private val tokenManager: Lazy<TokenManager>) : Interceptor {
        private val authMutex = Mutex()
        private inner class UnauthorizedException(val err: String) : Exception(err)

        override fun intercept(chain: Interceptor.Chain): Response {
            val oldToken = tokenManager.value.token()
            return try {
                val response = chain.proceed(with(tokenManager.value) { chain.request().newBuilder().addToken() }.build())
                if (response.isUnauthorized()) {
                    response.close()
                    Log.i(TAG, "Unauthorized, requesting new token")
                    runBlocking {
                        authMutex.withLock { tokenManager.value.refresh(oldToken) }
                    }
                    throw UnauthorizedException("Unauthorized")
                } else {
                    response
                }
            } catch (_: UnauthorizedException) {
                Log.i(TAG, "Was Unauthorizied, re-running with new token")
                chain.proceed(with(tokenManager.value) { chain.request().newBuilder().addToken() }.build())
            }
        }

        private fun Response.isUnauthorized(): Boolean = this.code == 401
    }

    private val baseUrl by lazy { getPrefBaseUrl() }
    private val baseAuthMode by lazy { getPrefBaseAuthMode() }
    private val baseLogin by lazy { getPrefBaseLogin() }
    private val basePassword by lazy { getPrefBasePassword() }

    private val tokenManager = lazy {
        TokenManager(
            baseAuthMode,
            baseLogin,
            basePassword,
            baseUrl,
            network.client.newBuilder()
                .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
                .build(),
        )
    }

    private val client: OkHttpClient =
        network.client.newBuilder()
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
            .addInterceptor(OkAuthorizationInterceptor(tokenManager))
            .build()

    private fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        tokenManager.value.getHeaders().forEach {
            add(it.name, it.value)
        }
    }

    suspend fun getTrackSearch(trackUrl: String): TrackSearch = withIOContext {
        val url = try {
            // test if getting api url or manga id
            val mangaId = trackUrl.toLong()
            "$baseUrl/api/v1/manga/$mangaId"
        } catch (e: NumberFormatException) {
            trackUrl
        }

        val manga = with(json) {
            client.newCall(GET("$url/full", headersBuilder().build()))
                .awaitSuccess()
                .parseAs<MangaDataClass>()
        }

        TrackSearch.create(trackId).apply {
            title = manga.title
            cover_url = "$url/thumbnail"
            summary = manga.description.orEmpty()
            tracking_url = url
            total_chapters = manga.chapterCount
            publishing_status = manga.status
            last_chapter_read = manga.lastChapterRead?.chapterNumber ?: 0.0
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
            client.newCall(GET("$url/chapters", headersBuilder().build()))
                .awaitSuccess()
                .parseAs<List<ChapterDataClass>>()
        }
        val lastChapterIndex = chapters.first { it.chapterNumber == track.last_chapter_read }.index

        client.newCall(
            PUT(
                "$url/chapter/$lastChapterIndex",
                headersBuilder().build(),
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

    public enum class AuthMode(val title: String) {
        NONE("None"),
        BASIC_AUTH("Basic Authentication"),
        SIMPLE_LOGIN("Simple Login"),
        UI_LOGIN("UI Login"),
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$sourceId", Context.MODE_PRIVATE)
    }

    private fun getPrefBaseAuthMode(): AuthMode {
        if (!preferences.contains(MODE_TITLE) && basePassword.isNotEmpty() && baseLogin.isNotEmpty()) {
            return AuthMode.BASIC_AUTH
        }
        return AuthMode.valueOf(preferences.getString(MODE_TITLE, MODE_DEFAULT)!!)
    }
    private fun getPrefBaseUrl(): String = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
    private fun getPrefBaseLogin(): String = preferences.getString(LOGIN_TITLE, LOGIN_DEFAULT)!!
    private fun getPrefBasePassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!
}

private const val ADDRESS_TITLE = "Server URL Address"
private const val ADDRESS_DEFAULT = ""
private const val MODE_TITLE = "Login Mode"
private const val MODE_DEFAULT = "NONE"
private const val LOGIN_TITLE = "Login (Basic Auth)"
private const val LOGIN_DEFAULT = ""
private const val PASSWORD_TITLE = "Password (Basic Auth)"
private const val PASSWORD_DEFAULT = ""
private const val TAG = "Suwayomi.API"
