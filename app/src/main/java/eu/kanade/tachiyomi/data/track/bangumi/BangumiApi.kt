package eu.kanade.tachiyomi.data.track.bangumi

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BangumiApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: BangumiInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            val body = FormBody.Builder()
                .add("rating", track.score.toInt().toString())
                .add("status", track.toBangumiStatus())
                .build()
            authClient.newCall(POST("$apiUrl/collection/${track.remote_id}/update", body = body))
                .awaitSuccess()
            track
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            // read status update
            val sbody = FormBody.Builder()
                .add("rating", track.score.toInt().toString())
                .add("status", track.toBangumiStatus())
                .build()
            authClient.newCall(POST("$apiUrl/collection/${track.remote_id}/update", body = sbody))
                .awaitSuccess()

            // chapter update
            val body = FormBody.Builder()
                .add("watched_eps", track.last_chapter_read.toInt().toString())
                .build()
            authClient.newCall(
                POST(
                    "$apiUrl/subject/${track.remote_id}/update/watched_eps",
                    body = body,
                ),
            ).awaitSuccess()

            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val url = "$apiUrl/search/subject/${URLEncoder.encode(search, StandardCharsets.UTF_8.name())}"
                .toUri()
                .buildUpon()
                .appendQueryParameter("max_results", "20")
                .build()
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .use {
                    var responseBody = it.body.string()
                    if (responseBody.isEmpty()) {
                        throw Exception("Null Response")
                    }
                    if (responseBody.contains("\"code\":404")) {
                        responseBody = "{\"results\":0,\"list\":[]}"
                    }
                    val response = json.decodeFromString<JsonObject>(responseBody)["list"]?.jsonArray
                    response?.filter { it.jsonObject["type"]?.jsonPrimitive?.int == 1 }
                        ?.map { jsonToSearch(it.jsonObject) }.orEmpty()
                }
        }
    }

    private fun jsonToSearch(obj: JsonObject): TrackSearch {
        val coverUrl = if (obj["images"] is JsonObject) {
            obj["images"]?.jsonObject?.get("common")?.jsonPrimitive?.contentOrNull ?: ""
        } else {
            // Sometimes JsonNull
            ""
        }
        val totalChapters = if (obj["eps_count"] != null) {
            obj["eps_count"]!!.jsonPrimitive.int
        } else {
            0
        }
        val rating = obj["rating"]?.jsonObject?.get("score")?.jsonPrimitive?.floatOrNull ?: -1f
        return TrackSearch.create(trackId).apply {
            remote_id = obj["id"]!!.jsonPrimitive.long
            title = obj["name_cn"]!!.jsonPrimitive.content
            cover_url = coverUrl
            summary = obj["name"]!!.jsonPrimitive.content
            score = rating
            tracking_url = obj["url"]!!.jsonPrimitive.content
            total_chapters = totalChapters
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$apiUrl/subject/${track.remote_id}"))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let { jsonToSearch(it) }
            }
        }
    }

    suspend fun statusLibManga(track: Track): Track? {
        return withIOContext {
            val urlUserRead = "$apiUrl/collection/${track.remote_id}"
            val requestUserRead = Request.Builder()
                .url(urlUserRead)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .get()
                .build()

            // TODO: get user readed chapter here
            val response = authClient.newCall(requestUserRead).awaitSuccess()
            val responseBody = response.body.string()
            if (responseBody.isEmpty()) {
                throw Exception("Null Response")
            }
            if (responseBody.contains("\"code\":400")) {
                null
            } else {
                json.decodeFromString<Collection>(responseBody).let {
                    track.status = it.status?.id!!
                    track.last_chapter_read = it.ep_status!!.toFloat()
                    track.score = it.rating!!
                    track
                }
            }
        }
    }

    suspend fun accessToken(code: String): OAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        oauthUrl,
        body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("redirect_uri", redirectUrl)
            .build(),
    )

    companion object {
        private const val clientId = "bgm10555cda0762e80ca"
        private const val clientSecret = "8fff394a8627b4c388cbf349ec865775"

        private const val apiUrl = "https://api.bgm.tv"
        private const val oauthUrl = "https://bgm.tv/oauth/access_token"
        private const val loginUrl = "https://bgm.tv/oauth/authorize"

        private const val redirectUrl = "tachiyomi://bangumi-auth"

        fun authUrl(): Uri =
            loginUrl.toUri().buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUrl)
                .build()

        fun refreshTokenRequest(token: String) = POST(
            oauthUrl,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", token)
                .add("redirect_uri", redirectUrl)
                .build(),
        )
    }
}
