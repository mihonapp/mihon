package eu.kanade.tachiyomi.data.track.mangabaka

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaListResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaSearchResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaUserInfo
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.math.RoundingMode
import java.util.Locale
import kotlin.time.Instant
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaBakaApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: MangaBakaInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            val url = "$LIBRARY_API_URL/${track.remote_id}"
            val body = buildJsonObject {
                put("is_private", track.private)
                put("state", track.toApiStatus())
                if (track.last_chapter_read > 0.0) {
                    put("progress_chapter", track.last_chapter_read)
                }
                if (track.score > 0) {
                    put("rating", track.score.toInt().coerceIn(0, 100))
                }
                if (track.started_reading_date > 0) {
                    put("start_date", track.started_reading_date.toLocalDate().toString())
                }
                if (track.finished_reading_date > 0) {
                    put("finish_date", track.finished_reading_date.toLocalDate().toString())
                }
            }
                .toString()
                .toRequestBody()

            authClient
                .newCall(POST(url, body = body, headers = headersOf("Content-Type", APP_JSON)))
                .awaitSuccess()

            // only returns 201 with the body { "status": 201, "data": true }, so no library ID for us
            track
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            val url = "$LIBRARY_API_URL/${track.remoteId}"

            authClient
                .newCall(DELETE(url))
                .awaitSuccess()
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            with(json) {
                try {
                    val url = "$LIBRARY_API_URL/${track.remote_id}"
                    val userData = authClient.newCall(GET(url))
                        .awaitSuccess()
                        .parseAs<MangaBakaListResult>()
                        .data

                    val additionalData = authClient.newCall(GET("$API_BASE_URL/v1/series/${track.remote_id}"))
                        .awaitSuccess()
                        .parseAs<MangaBakaItemResult>()
                        .data

                    Track.create(TrackerManager.MANGABAKA).apply {
                        remote_id = track.remote_id
                        title = additionalData.title
                        status = userData.getStatus()
                        score = userData.rating?.toDouble() ?: 0.0
                        started_reading_date = userData.startDate?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
                        finished_reading_date =
                            userData.finishDate?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
                        last_chapter_read = userData.progressChapter ?: 0.0
                        private = userData.isPrivate
                    }
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        null
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            val url = "$LIBRARY_API_URL/${track.remote_id}"
            val body = buildJsonObject {
                put("state", track.toApiStatus())
                put("is_private", track.private)
                if (track.last_chapter_read > 0.0) {
                    put("progress_chapter", track.last_chapter_read)
                } else {
                    put("progress_chapter", null)
                }
                if (track.score > 0) {
                    put("rating", track.score.toInt().coerceIn(0, 100))
                } else {
                    put("rating", null)
                }
                if (track.started_reading_date > 0) {
                    put("start_date", track.started_reading_date.toLocalDate().toString())
                } else {
                    put("start_date", null)
                }
                if (track.finished_reading_date > 0) {
                    put("finish_date", track.finished_reading_date.toLocalDate().toString())
                } else {
                    put("finish_date", null)
                }
            }
                .toString()
                .toRequestBody()

            authClient
                .newCall(PUT(url, body = body, headers = headersOf("Content-Type", APP_JSON)))
                .awaitSuccess()

            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val url = "$API_BASE_URL/v1/series/search".toUri().buildUpon()
                .appendQueryParameter("q", search)
                .appendQueryParameter("type_not", "novel")
                .build()
            with(json) {
                client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MangaBakaSearchResult>()
                    .data
                    .map { parseSearchItem(it) }
            }
        }
    }

    private fun parseSearchItem(item: MangaBakaItem): TrackSearch {
        return TrackSearch.create(trackId).apply {
            remote_id = item.id
            title = item.title
            summary = item.description?.trim().orEmpty()
            score = item.rating?.toBigDecimal()?.setScale(2, RoundingMode.HALF_UP)?.toDouble() ?: -1.0
            cover_url = item.cover.x250.x1.orEmpty()
            tracking_url = "$BASE_URL/${item.id}"
            start_date = item.year?.toString().orEmpty()
            publishing_status = item.status
            publishing_type = item.type.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            }
            authors = item.authors.orEmpty()
            artists = item.artists.orEmpty()
        }
    }

    suspend fun getMangaDetails(id: Int): TrackSearch? {
        return withIOContext {
            val url = "$API_BASE_URL/v1/series".toUri().buildUpon()
                .appendPath(id.toString())
                .build()
            with(json) {
                try {
                    authClient.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<MangaBakaItemResult>()
                        .data
                        .let { parseSearchItem(it) }
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        return@with null
                    }
                    throw e
                }
            }
        }
    }

    suspend fun getScoreStepSize(): Int {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$OAUTH_URL/userinfo"))
                    .awaitSuccess()
                    .parseAs<MangaBakaUserInfo>()
                    .ratingSteps
            }
        }
    }

    suspend fun getAccessToken(code: String): MangaBakaOAuth {
        return withIOContext {
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("code_challenge_method", "S256")
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .add("scope", SCOPES)
                .build()

            with(json) {
                client.newCall(POST("${OAUTH_URL}/token", body = formBody))
                    .awaitSuccess().parseAs()
            }
        }
    }

    companion object {
        private const val CLIENT_ID = ""

        private const val BASE_URL = "https://mangabaka.org"
        private const val API_BASE_URL = "https://api.mangabaka.dev"
        private const val LIBRARY_API_URL = "$API_BASE_URL/v1/my/library"
        private const val OAUTH_URL = "$BASE_URL/auth/oauth2"
        private const val SCOPES = "library.read library.write offline_access openid"

        private const val REDIRECT_URI = "mihon://mangabaka-auth"

        private const val APP_JSON = "application/json"

        private var codeVerifier: String = ""

        fun authUrl(): Uri = "$OAUTH_URL/authorize".toUri().buildUpon() //
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("code_challenge", getPkceS256ChallengeCode())
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .build()

        fun refreshTokenRequest(token: String) = POST(
            "$OAUTH_URL/token",
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("refresh_token", token)
                .add("redirect_uri", REDIRECT_URI)
                .build(),
        )

        private fun getPkceS256ChallengeCode(): String {
            // MangaBaka requires an actually conformant PKCE process, unlike MAL
            // 1. create verifier
            // 2. create challenge from verifier (S256 hash -> base64 URL encode)
            // 3. send challenge to /authorize
            // 4. send verifier for access tokens to /token
            val codes = PkceUtil.generateS256Codes()
            codeVerifier = codes.codeVerifier
            return codes.codeChallenge
        }
    }
}
