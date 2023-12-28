package eu.kanade.tachiyomi.data.track.kitsu

import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import tachiyomi.domain.track.model.Track as DomainTrack

class KitsuApi(private val client: OkHttpClient, interceptor: KitsuInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track, userId: String): Track {
        return withIOContext {
            val data = buildJsonObject {
                putJsonObject("data") {
                    put("type", "libraryEntries")
                    putJsonObject("attributes") {
                        put("status", track.toKitsuStatus())
                        put("progress", track.last_chapter_read.toInt())
                    }
                    putJsonObject("relationships") {
                        putJsonObject("user") {
                            putJsonObject("data") {
                                put("id", userId)
                                put("type", "users")
                            }
                        }
                        putJsonObject("media") {
                            putJsonObject("data") {
                                put("id", track.remote_id)
                                put("type", "manga")
                            }
                        }
                    }
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        "${baseUrl}library-entries",
                        headers = headersOf(
                            "Content-Type",
                            "application/vnd.api+json",
                        ),
                        body = data.toString()
                            .toRequestBody("application/vnd.api+json".toMediaType()),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        track.remote_id = it["data"]!!.jsonObject["id"]!!.jsonPrimitive.long
                        track
                    }
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            val data = buildJsonObject {
                putJsonObject("data") {
                    put("type", "libraryEntries")
                    put("id", track.remote_id)
                    putJsonObject("attributes") {
                        put("status", track.toKitsuStatus())
                        put("progress", track.last_chapter_read.toInt())
                        put("ratingTwenty", track.toKitsuScore())
                        put("startedAt", KitsuDateHelper.convert(track.started_reading_date))
                        put("finishedAt", KitsuDateHelper.convert(track.finished_reading_date))
                    }
                }
            }

            with(json) {
                authClient.newCall(
                    Request.Builder()
                        .url("${baseUrl}library-entries/${track.remote_id}")
                        .headers(
                            headersOf(
                                "Content-Type",
                                "application/vnd.api+json",
                            ),
                        )
                        .patch(
                            data.toString().toRequestBody("application/vnd.api+json".toMediaType()),
                        )
                        .build(),
                )
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        track
                    }
            }
        }
    }

    suspend fun removeLibManga(track: DomainTrack) {
        withIOContext {
            authClient
                .newCall(
                    DELETE(
                        "${baseUrl}library-entries/${track.remoteId}",
                        headers = headersOf(
                            "Content-Type",
                            "application/vnd.api+json",
                        ),
                    ),
                )
                .awaitSuccess()
        }
    }
    suspend fun search(query: String): List<TrackSearch> {
        return withIOContext {
            with(json) {
                authClient.newCall(GET(algoliaKeyUrl))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        val key = it["media"]!!.jsonObject["key"]!!.jsonPrimitive.content
                        algoliaSearch(key, query)
                    }
            }
        }
    }

    private suspend fun algoliaSearch(key: String, query: String): List<TrackSearch> {
        return withIOContext {
            val jsonObject = buildJsonObject {
                put("params", "query=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}$algoliaFilter")
            }

            with(json) {
                client.newCall(
                    POST(
                        algoliaUrl,
                        headers = headersOf(
                            "X-Algolia-Application-Id",
                            algoliaAppId,
                            "X-Algolia-API-Key",
                            key,
                        ),
                        body = jsonObject.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        it["hits"]!!.jsonArray
                            .map { KitsuSearchManga(it.jsonObject) }
                            .filter { it.subType != "novel" }
                            .map { it.toTrack() }
                    }
            }
        }
    }

    suspend fun findLibManga(track: Track, userId: String): Track? {
        return withIOContext {
            val url = "${baseUrl}library-entries".toUri().buildUpon()
                .encodedQuery("filter[manga_id]=${track.remote_id}&filter[user_id]=$userId")
                .appendQueryParameter("include", "manga")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        val data = it["data"]!!.jsonArray
                        if (data.size > 0) {
                            val manga = it["included"]!!.jsonArray[0].jsonObject
                            KitsuLibManga(data[0].jsonObject, manga).toTrack()
                        } else {
                            null
                        }
                    }
            }
        }
    }

    suspend fun getLibManga(track: Track): Track {
        return withIOContext {
            val url = "${baseUrl}library-entries".toUri().buildUpon()
                .encodedQuery("filter[id]=${track.remote_id}")
                .appendQueryParameter("include", "manga")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        val data = it["data"]!!.jsonArray
                        if (data.size > 0) {
                            val manga = it["included"]!!.jsonArray[0].jsonObject
                            KitsuLibManga(data[0].jsonObject, manga).toTrack()
                        } else {
                            throw Exception("Could not find manga")
                        }
                    }
            }
        }
    }

    suspend fun login(username: String, password: String): OAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("grant_type", "password")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()
            with(json) {
                client.newCall(POST(loginUrl, body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): String {
        return withIOContext {
            val url = "${baseUrl}users".toUri().buildUpon()
                .encodedQuery("filter[self]=true")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        it["data"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
                    }
            }
        }
    }

    companion object {
        private const val clientId =
            "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val clientSecret =
            "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

        private const val baseUrl = "https://kitsu.io/api/edge/"
        private const val loginUrl = "https://kitsu.io/api/oauth/token"
        private const val baseMangaUrl = "https://kitsu.io/manga/"
        private const val algoliaKeyUrl = "https://kitsu.io/api/edge/algolia-keys/media/"

        private const val algoliaUrl =
            "https://AWQO5J657S-dsn.algolia.net/1/indexes/production_media/query/"
        private const val algoliaAppId = "AWQO5J657S"
        private const val algoliaFilter =
            "&facetFilters=%5B%22kind%3Amanga%22%5D&attributesToRetrieve=" +
                "%5B%22synopsis%22%2C%22averageRating%22%2C%22canonicalTitle%22%2C%22chapterCount%22%2C%22" +
                "posterImage%22%2C%22startDate%22%2C%22subtype%22%2C%22endDate%22%2C%20%22id%22%5D"

        fun mangaUrl(remoteId: Long): String {
            return baseMangaUrl + remoteId
        }

        fun refreshTokenRequest(token: String) = POST(
            loginUrl,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", token)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build(),
        )
    }
}
