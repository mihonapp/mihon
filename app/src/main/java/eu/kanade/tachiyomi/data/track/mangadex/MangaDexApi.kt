package eu.kanade.tachiyomi.data.track.mangadex

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexChapter
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexChapterListResult
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexMangaResult
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexOAuth
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexReadMarkersResult
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexSearchResult
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexStatusResult
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexTokenResponse
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexUserResult
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.seconds

class MangaDexApi(
    private val trackerId: Long,
    baseClient: OkHttpClient,
    interceptor: MangaDexInterceptor,
) {

    private val json: Json by injectLazy()

    private val client = baseClient.newBuilder()
        .rateLimit(permits = 5, period = 1.seconds)
        .addInterceptor {
            it.request().newBuilder()
                .header(
                    "User-Agent",
                    buildString {
                        append("Mihon/v${BuildConfig.VERSION_NAME} ")
                        append("(${BuildConfig.APPLICATION_ID}) ${BuildConfig.COMMIT_SHA}) ")
                        append("(Android) (https://github.com/mihonapp/mihon)")
                    },
                )
                .header("Accept", jsonMime.toString())
                .build()
                .let(it::proceed)
        }.build()

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .build()

    suspend fun search(query: String): List<TrackSearch> {
        return withContext(Dispatchers.IO) {
            val url = "$API_URL/manga".toUri().buildUpon()
                .appendQueryParameter("title", query)
                .appendQueryParameter("limit", "20")
                .appendQueryParameter("includes[]", "cover_art")
                .appendQueryParameter("order[followedCount]", "desc")
                .apply { CONTENT_RATINGS.forEach { appendQueryParameter("contentRating[]", it) } }
                .build()

            with(json) {
                client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MangaDexSearchResult>()
                    .data
                    .map { it.toTrackSearch(trackerId) }
            }
        }
    }

    suspend fun getMangaDetails(uuid: String): TrackSearch? {
        return withContext(Dispatchers.IO) {
            val url = "$API_URL/manga/$uuid".toUri().buildUpon()
                .appendQueryParameter("includes[]", "cover_art")
                .build()

            with(json) {
                try {
                    authClient.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<MangaDexMangaResult>()
                        .data
                        .toTrackSearch(trackerId)
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        return@with null
                    }
                    throw e
                }
            }
        }
    }

    /**
     * Returns the reading status of a manga, or null when the user has no
     * reading status (or is not following) the manga.
     */
    suspend fun getStatusOrNull(uuid: String): String? {
        return withContext(Dispatchers.IO) {
            with(json) {
                try {
                    authClient.newCall(GET("$API_URL/manga/$uuid/status"))
                        .awaitSuccess()
                        .parseAs<MangaDexStatusResult>()
                        .status
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        return@with null
                    }
                    throw e
                }
            }
        }
    }

    suspend fun setStatus(uuid: String, status: Long) {
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("status", status.toMdStatus())
            }
                .toString()
                .toRequestBody(jsonMime)

            authClient.newCall(POST("$API_URL/manga/$uuid/status", body = body))
                .awaitSuccess()
                .close()
        }
    }

    suspend fun followManga(uuid: String) {
        withContext(Dispatchers.IO) {
            authClient.newCall(POST("$API_URL/manga/$uuid/follow"))
                .awaitSuccess()
                .close()
        }
    }

    suspend fun unfollowManga(uuid: String) {
        withContext(Dispatchers.IO) {
            authClient.newCall(DELETE("$API_URL/manga/$uuid/follow"))
                .awaitSuccess()
                .close()
        }
    }

    suspend fun getReadChapterIds(uuid: String): Set<String> {
        return withContext(Dispatchers.IO) {
            with(json) {
                authClient.newCall(GET("$API_URL/manga/$uuid/read"))
                    .awaitSuccess()
                    .parseAs<MangaDexReadMarkersResult>()
                    .chapters
                    .toSet()
            }
        }
    }

    /**
     * Fetches the id and number of every chapter, ordered by number ascending,
     * stopping early once [upToChapter] is passed to avoid walking the whole feed.
     */
    suspend fun getChapters(uuid: String, upToChapter: Float = Float.MAX_VALUE): List<MangaDexChapter> {
        return withContext(Dispatchers.IO) {
            val chapters = mutableListOf<MangaDexChapter>()
            var offset = 0
            var total = 0L
            do {
                val url = "$API_URL/manga/$uuid/feed".toUri().buildUpon()
                    .appendQueryParameter("limit", CHAPTER_PAGE_LIMIT.toString())
                    .appendQueryParameter("offset", offset.toString())
                    .appendQueryParameter("order[chapter]", "asc")
                    .apply { CONTENT_RATINGS.forEach { appendQueryParameter("contentRating[]", it) } }
                    .build()

                val page = with(json) {
                    authClient.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<MangaDexChapterListResult>()
                }
                total = page.total
                offset += page.data.size
                chapters += page.data
            } while (
                offset < total &&
                page.data.isNotEmpty() &&
                (chapters.lastOrNull()?.number ?: Float.MAX_VALUE) <= upToChapter
            )

            chapters
        }
    }

    suspend fun markChaptersRead(uuid: String, chapterIds: List<String>) {
        withContext(Dispatchers.IO) {
            chapterIds.chunked(MARKER_BATCH_LIMIT).forEach { chunk ->
                val body = buildJsonObject {
                    putJsonArray("chapterIdsRead") {
                        chunk.forEach { add(it) }
                    }
                }
                    .toString()
                    .toRequestBody(jsonMime)

                authClient.newCall(POST("$API_URL/manga/$uuid/read", body = body))
                    .awaitSuccess()
                    .close()
            }
        }
    }

    suspend fun getCurrentUser(): MangaDexUserResult {
        return withContext(Dispatchers.IO) {
            with(json) {
                authClient.newCall(GET("$API_URL/user/me"))
                    .awaitSuccess()
                    .parseAs<MangaDexUserResult>()
            }
        }
    }

    suspend fun getAccessToken(code: String): MangaDexOAuth {
        return withContext(Dispatchers.IO) {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            with(json) {
                client.newCall(POST(TOKEN_URL, body = formBody))
                    .awaitSuccess()
                    .parseAs<MangaDexTokenResponse>()
                    .toOauth()
            }
        }
    }

    companion object {
        // TODO: MangaDex grants public clients individually; this id must be
        // registered with MangaDex staff before the tracker becomes usable
        private const val CLIENT_ID = "mihon"
        private const val REDIRECT_URI = "app.mihon://mangadex-auth"

        private const val API_URL = "https://api.mangadex.org"
        private const val AUTH_SERVER_URL = "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect"
        private const val TOKEN_URL = "$AUTH_SERVER_URL/token"

        private val CONTENT_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")

        // MangaDex limits read marker batches to 100 ids and feed pages to 500 chapters
        private const val MARKER_BATCH_LIMIT = 100
        private const val CHAPTER_PAGE_LIMIT = 500

        private var codeVerifier: String = ""

        fun authUrl(): Uri = "$AUTH_SERVER_URL/auth".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", getPkceS256ChallengeCode())
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        fun refreshTokenRequest(oauth: MangaDexOAuth) = POST(
            TOKEN_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("refresh_token", oauth.refreshToken)
                .build(),
            headers = Headers.headersOf("Accept", jsonMime.toString()),
        )

        private fun getPkceS256ChallengeCode(): String {
            val codes = PkceUtil.generateS256Codes()
            codeVerifier = codes.codeVerifier
            return codes.codeChallenge
        }
    }
}
