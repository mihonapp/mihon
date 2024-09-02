package eu.kanade.tachiyomi.data.track.kitsu

import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuAddMangaResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuAlgoliaSearchResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuCurrentUserResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuListSearchResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchResult
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
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
                        put("status", track.toApiStatus())
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
                        "${BASE_URL}library-entries",
                        headers = headersOf("Content-Type", VND_API_JSON),
                        body = data.toString().toRequestBody(VND_JSON_MEDIA_TYPE),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuAddMangaResult>()
                    .let {
                        track.remote_id = it.data.id
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
                        put("status", track.toApiStatus())
                        put("progress", track.last_chapter_read.toInt())
                        put("ratingTwenty", track.toApiScore())
                        put("startedAt", KitsuDateHelper.convert(track.started_reading_date))
                        put("finishedAt", KitsuDateHelper.convert(track.finished_reading_date))
                    }
                }
            }

            authClient.newCall(
                Request.Builder()
                    .url("${BASE_URL}library-entries/${track.remote_id}")
                    .headers(
                        headersOf("Content-Type", VND_API_JSON),
                    )
                    .patch(data.toString().toRequestBody(VND_JSON_MEDIA_TYPE))
                    .build(),
            )
                .awaitSuccess()

            track
        }
    }

    suspend fun removeLibManga(track: DomainTrack) {
        withIOContext {
            authClient.newCall(
                DELETE(
                    "${BASE_URL}library-entries/${track.remoteId}",
                    headers = headersOf("Content-Type", VND_API_JSON),
                ),
            )
                .awaitSuccess()
        }
    }

    suspend fun search(query: String): List<TrackSearch> {
        return withIOContext {
            with(json) {
                authClient.newCall(GET(ALGOLIA_KEY_URL))
                    .awaitSuccess()
                    .parseAs<KitsuSearchResult>()
                    .let {
                        algoliaSearch(it.media.key, query)
                    }
            }
        }
    }

    private suspend fun algoliaSearch(key: String, query: String): List<TrackSearch> {
        return withIOContext {
            val jsonObject = buildJsonObject {
                put("params", "query=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}$ALGOLIA_FILTER")
            }

            with(json) {
                client.newCall(
                    POST(
                        ALGOLIA_URL,
                        headers = headersOf(
                            "X-Algolia-Application-Id",
                            ALGOLIA_APP_ID,
                            "X-Algolia-API-Key",
                            key,
                        ),
                        body = jsonObject.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuAlgoliaSearchResult>()
                    .hits
                    .filter { it.subtype != "novel" }
                    .map { it.toTrack() }
            }
        }
    }

    suspend fun findLibManga(track: Track, userId: String): Track? {
        return withIOContext {
            val url = "${BASE_URL}library-entries".toUri().buildUpon()
                .encodedQuery("filter[manga_id]=${track.remote_id}&filter[user_id]=$userId")
                .appendQueryParameter("include", "manga")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<KitsuListSearchResult>()
                    .let {
                        if (it.data.isNotEmpty() && it.included.isNotEmpty()) {
                            it.firstToTrack()
                        } else {
                            null
                        }
                    }
            }
        }
    }

    suspend fun getLibManga(track: Track): Track {
        return withIOContext {
            val url = "${BASE_URL}library-entries".toUri().buildUpon()
                .encodedQuery("filter[id]=${track.remote_id}")
                .appendQueryParameter("include", "manga")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<KitsuListSearchResult>()
                    .let {
                        if (it.data.isNotEmpty() && it.included.isNotEmpty()) {
                            it.firstToTrack()
                        } else {
                            throw Exception("Could not find manga")
                        }
                    }
            }
        }
    }

    suspend fun login(username: String, password: String): KitsuOAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("grant_type", "password")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            with(json) {
                client.newCall(POST(LOGIN_URL, body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): String {
        return withIOContext {
            val url = "${BASE_URL}users".toUri().buildUpon()
                .encodedQuery("filter[self]=true")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<KitsuCurrentUserResult>()
                    .data[0]
                    .id
            }
        }
    }

    companion object {
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

        private const val BASE_URL = "https://kitsu.app/api/edge/"
        private const val LOGIN_URL = "https://kitsu.app/api/oauth/token"
        private const val BASE_MANGA_URL = "https://kitsu.app/manga/"
        private const val ALGOLIA_KEY_URL = "https://kitsu.app/api/edge/algolia-keys/media/"

        private const val ALGOLIA_APP_ID = "AWQO5J657S"
        private const val ALGOLIA_URL = "https://$ALGOLIA_APP_ID-dsn.algolia.net/1/indexes/production_media/query/"
        private const val ALGOLIA_FILTER = "&facetFilters=%5B%22kind%3Amanga%22%5D&attributesToRetrieve=" +
            "%5B%22synopsis%22%2C%22averageRating%22%2C%22canonicalTitle%22%2C%22chapterCount%22%2C%22" +
            "posterImage%22%2C%22startDate%22%2C%22subtype%22%2C%22endDate%22%2C%20%22id%22%5D"

        private const val VND_API_JSON = "application/vnd.api+json"
        private val VND_JSON_MEDIA_TYPE = VND_API_JSON.toMediaType()

        fun mangaUrl(remoteId: Long): String {
            return BASE_MANGA_URL + remoteId
        }

        fun refreshTokenRequest(token: String) = POST(
            LOGIN_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", token)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build(),
        )
    }
}
