package eu.kanade.tachiyomi.data.track.shikimori

import android.net.Uri
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMLibraryIdResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUser
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import mihon.graphql.shikimori.ShikimoriGetCurrentUserQuery
import mihon.graphql.shikimori.ShikimoriGetLibMangaQuery
import mihon.graphql.shikimori.ShikimoriGetMangaDetailsQuery
import mihon.graphql.shikimori.ShikimoriSearchMangaQuery
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class ShikimoriApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("$API_URL/graphql")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            .build()
    }

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
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        // save id of the entry for possible future delete request
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            val payload = buildJsonObject {
                putJsonObject("user_rate") {
                    put("chapters", track.last_chapter_read.toInt())
                    put("score", track.score.toInt())
                    put("status", track.toShikimoriStatus())
                }
            }

            with(json) {
                authClient.newCall(
                    PUT(
                        "$API_URL/v2/user_rates/${track.library_id}",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        val response = graphQlClient
            .query(
                ShikimoriSearchMangaQuery(search = search),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            data.mangas.map { it.toTrackSearch(trackId) }
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "Shikimori Search error: ${it.message}" } }
            }
            emptyList()
        }
    }

    suspend fun getMangaDetails(id: Int): TrackSearch? {
        val response = graphQlClient
            .query(
                ShikimoriGetMangaDetailsQuery(query = "$id"),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            data.mangas
                .firstOrNull()
                ?.toTrackSearch(trackId)
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "Shikimori Get Details error: ${it.message}" } }
            }
            null
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        val response = graphQlClient
            .query(
                ShikimoriGetLibMangaQuery(
                    remote_id = track.remote_id.toString(),
                ),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            val mangaResult = data.mangas.firstOrNull()

            // Shikimori has no user list query that allows query by ID, so we go via the "mangas" query & include
            // userRate data which will be null if the title is not in the user's list.
            // If it was removed on Shikimori and is still linked in the app, notify user via returning null here
            // which throws an exception at the Shikimori.refresh call
            if (mangaResult?.userRate == null) {
                null
            } else {
                mangaResult.toTrack(trackId)
            }
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "Shikimori Find error: ${it.message}" } }
            }
            null
        }
    }

    suspend fun getCurrentUser(): SMUser {
        val response = graphQlClient
            .query(
                ShikimoriGetCurrentUserQuery(),
            )
            .awaitSuccess()

        val data = response.data
        return if (data?.currentUser != null) {
            SMUser(id = data.currentUser.id, nickname = data.currentUser.nickname)
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "Shikimori Get User error: ${it.message}" } }
            }
            null
        }
            ?: throw Exception("Failed to get Shikimori user data")
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
        OAUTH_URL,
        body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URL)
            .build(),
    )

    companion object {
        private const val BASE_URL = "https://shikimori.io"
        private const val API_URL = "$BASE_URL/api"
        private const val OAUTH_URL = "$BASE_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"

        private const val REDIRECT_URL = "mihon://shikimori-auth"

        private const val CLIENT_ID = "PB9dq8DzI405s7wdtwTdirYqHiyVMh--djnP7lBUqSA"
        private const val CLIENT_SECRET = "NajpZcOBKB9sJtgNcejf8OB9jBN1OYYoo-k4h2WWZus"

        fun authUrl(): Uri = LOGIN_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .appendQueryParameter("response_type", "code")
            .build()

        fun refreshTokenRequest(token: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", token)
                .build(),
        )
    }
}
