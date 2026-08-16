package eu.kanade.tachiyomi.data.track.shikimori

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMAddMangaResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMSearchResult
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUser
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserListResult
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserResult
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

data class ShikimoriConfig(
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String,
)

class ShikimoriApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
    config: ShikimoriConfig,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    private val apiUrl = "${config.baseUrl}/api"
    private val graphqlApiUrl = "$apiUrl/graphql"
    private val oauthUrl = "${config.baseUrl}/oauth/token"
    private val loginUrl = "${config.baseUrl}/oauth/authorize"
    private val clientId = config.clientId
    private val clientSecret = config.clientSecret

    suspend fun addLibManga(track: Track, userId: String): Track {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Manga")
                        put("chapters", track.last_chapter_read.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$apiUrl/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMAddMangaResponse>()
                    .let {
                        // save id of the entry for possible future delete request
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibManga(track: Track, userId: String): Track = addLibManga(track, userId)

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$apiUrl/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val query = $$"""
            |query($query: String) {
                |mangas(search: $query, limit: 20, kind:"!light_novel,!novel") {
                    |id
                    |name
                    |chapters
                    |kind
                    |poster {
                        |mainUrl
                    |}
                    |score
                    |url
                    |status
                    |airedOn {
                        |date
                    |}
                    |description
                    |personRoles {
                        |person {
                            |name
                        |}
                        |rolesEn
                    |}
                |}
            |}
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        graphqlApiUrl,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMSearchResult>()
                    .data.mangas
                    .map { it.toTrack(trackId) }
            }
        }
    }

    suspend fun getMangaDetails(id: Int): TrackSearch? {
        return withIOContext {
            val query = $$"""
            |query($query: String) {
                |mangas(ids: $query, limit: 1, kind:"!light_novel,!novel") {
                    |id
                    |name
                    |chapters
                    |kind
                    |poster {
                        |mainUrl
                    |}
                    |score
                    |url
                    |status
                    |airedOn {
                        |date
                    |}
                    |description
                    |personRoles {
                        |person {
                            |name
                        |}
                        |rolesEn
                    |}
                |}
            |}
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", "$id")
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        graphqlApiUrl,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMSearchResult>()
                    .data.mangas
                    .firstOrNull()
                    ?.toTrack(trackId)
            }
        }
    }

    suspend fun findLibManga(track: Track, isRefresh: Boolean = false): Track? {
        return withIOContext {
            val query = $$"""
                |query($id: String) {
                    |mangas(ids: $id, limit: 1) {
                        |id
                        |url
                        |name
                        |chapters
                        |userRate {
                            |id
                            |chapters
                            |status
                            |score
                        |}
                    |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("id", track.remote_id.toString())
                }
            }
            with(json) {
                val listResult = authClient.newCall(
                    POST(
                        graphqlApiUrl,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMUserListResult>()
                    .data.mangas
                    .firstOrNull()

                // Shikimori has no user list query that allows query by ID, so we go via the "mangas" query & include
                // userRate data which will be null if the title is not in the user's list.
                // If it was removed on Shikimori and is still linked in the app, notify user via returning null here
                // which throws an exception at the Shikimori.refresh call
                if (isRefresh && listResult?.userRate == null) return@with null

                listResult?.toTrack(trackId)
            }
        }
    }

    suspend fun getCurrentUser(): SMUser {
        return with(json) {
            val query = """
            |{
                |currentUser {
                    |id
                    |nickname
                |}
            |}
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
            }
            authClient.newCall(
                POST(
                    graphqlApiUrl,
                    body = payload.toString().toRequestBody(jsonMime),
                ),
            )
                .awaitSuccess()
                .parseAs<SMUserResult>()
                .data.currentUser
        }
    }

    suspend fun accessToken(code: String): SMOAuth {
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
            .add("redirect_uri", REDIRECT_URL)
            .build(),
    )

    fun authUrl(): Uri = loginUrl.toUri().buildUpon()
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("redirect_uri", REDIRECT_URL)
        .appendQueryParameter("response_type", "code")
        .build()

    companion object {
        const val DEFAULT_BASE_URL = "https://shikimori.io"

        private const val REDIRECT_URL = "mihon://shikimori-auth"

        fun refreshTokenRequest(config: ShikimoriConfig, token: String) = POST(
            "${config.baseUrl}/oauth/token",
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", config.clientId)
                .add("client_secret", config.clientSecret)
                .add("refresh_token", token)
                .build(),
        )
    }
}
