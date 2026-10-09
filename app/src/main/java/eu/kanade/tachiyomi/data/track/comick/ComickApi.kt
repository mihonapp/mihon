package eu.kanade.tachiyomi.data.track.comick

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.comick.dto.ComickLibraryEntry
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.data.track.comick.dto.ComickSearchResult
import eu.kanade.tachiyomi.data.track.comick.dto.ComickTitleLookupResult
import eu.kanade.tachiyomi.data.track.comick.dto.ComickUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import tachiyomi.domain.track.model.Track as DomainTrack

class ComickApi(
    private val trackerId: Long,
    baseClient: OkHttpClient,
    interceptor: ComickInterceptor,
) {

    private val json: Json by injectLazy()

    private val client = baseClient.newBuilder()
        .rateLimit(permits = 55, period = 1.minutes)
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

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            val url = "$RESOURCE_URL/me/library".toUri().buildUpon()
                .appendPath(track.remote_id.toHid())
                .build()

            val body = buildJsonObject {
                put("status", track.status.toApiListStatus())
                putJsonObject("progress") {
                    put("number", track.last_chapter_read.takeIf { it > 0.0 }?.toString())
                }
            }
                .toString()
                .toRequestBody(jsonMime)

            authClient
                .newCall(PUT(url.toString(), body = body))
                .awaitSuccess()

            // only returns the same data back with nothing additionally useful
            track
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            val url = "$RESOURCE_URL/me/library".toUri().buildUpon()
                .appendPath(track.remoteId.toHid())
                .build()

            authClient
                .newCall(DELETE(url.toString(), body = RequestBody.EMPTY))
                .awaitSuccess()
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            val url = "$RESOURCE_URL/me/library".toUri().buildUpon()
                .appendPath(track.remote_id.toHid())
                .build()
            with(json) {
                try {
                    authClient.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<ComickLibraryEntry>()
                        .toTrack(trackerId)
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        return@with null
                    }
                    throw e
                }
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            val url = "$RESOURCE_URL/me/library".toUri().buildUpon()
                .appendPath(track.remote_id.toHid())
                .build()

            val body = buildJsonObject {
                put("rating", track.score.toInt().takeIf { it > 0 })
                put("status", track.status.toApiListStatus())
                putJsonObject("progress") {
                    put("number", track.last_chapter_read.toString())
                }
            }
                .toString()
                .toRequestBody(jsonMime)

            val request = Request.Builder()
                .url(url.toString())
                .patch(body)
                .build()

            authClient.newCall(request)
                .awaitSuccess()

            // only returns the same data back with nothing additionally useful
            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val url = "$RESOURCE_URL/titles".toUri().buildUpon()
                .appendQueryParameter("q", search)
                .appendQueryParameter("media_type", "manga")
                .appendQueryParameter("limit", "20")
                .build()

            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<ComickSearchResult>()
                    .data
                    .map { it.toTrackSearch(trackerId) }
            }
        }
    }

    suspend fun getMangaDetails(hid: String): TrackSearch? {
        return withIOContext {
            val url = "$RESOURCE_URL/titles".toUri().buildUpon()
                .appendPath(hid)
                .build()

            with(json) {
                try {
                    authClient.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<ComickTitleLookupResult>()
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

    suspend fun getCurrentUser(): ComickUser {
        return withIOContext {
            val url = "$RESOURCE_URL/me"

            with(json) {
                authClient.newCall(GET(url))
                    .awaitSuccess()
                    .parseAs<ComickUser>()
            }
        }
    }

    /**
     * @throws ComickMissingScopesException Thrown when user does not grant offline_access or library:write
     */
    suspend fun getAccessToken(code: String): ComickOAuth {
        return withIOContext {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .add("resource", RESOURCE_URL)
                .build()

            with(json) {
                val response = client.newCall(
                    POST("${OAUTH_URL}/token", body = formBody),
                )
                    .awaitSuccess()

                val body = response.body.string()
                if (!body.contains("refresh_token")) {
                    throw ComickMissingScopesException("Permission for ongoing synchronisation is required.")
                }

                val oauth = json.decodeFromString<ComickOAuth>(body)
                if (!oauth.scope.contains(LIBRARY_WRITE_SCOPE)) {
                    // Users may forget to enable this since it's off by default
                    throw ComickMissingScopesException("Permission to add/remove manga from your library is required.")
                }

                oauth
            }
        }
    }

    fun verifyOAuthState(state: String): Boolean = state == oauthStateParam

    companion object {
        private const val CLIENT_ID = "" // TODO: Mihon client ID
        private const val REDIRECT_URI = "app.mihon://comick-auth"

        private const val LIBRARY_WRITE_SCOPE = "library:write"
        private const val SCOPES = "library:read $LIBRARY_WRITE_SCOPE offline_access"

        private const val OAUTH_URL = "https://comick.dev/api/auth/oauth2"
        private const val API_BASE_URL = "https://api.comick.dev"
        private const val RESOURCE_URL = "$API_BASE_URL/integrations/v1"

        private var codeVerifier: String = ""
        private var oauthStateParam: String = ""

        fun authUrl(): Uri = "$OAUTH_URL/authorize".toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("resource", RESOURCE_URL)
            .appendQueryParameter("state", getOAuthStateParam())
            .appendQueryParameter("code_challenge", getPkceS256ChallengeCode())
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        fun refreshTokenRequest(refreshToken: String) = POST(
            "$OAUTH_URL/token",
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("resource", RESOURCE_URL)
                .build(),
            headers = headersOf(
                "Accept",
                jsonMime.toString(),
            ),
        )

        private fun getOAuthStateParam(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            oauthStateParam = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)

            return oauthStateParam
        }

        private fun getPkceS256ChallengeCode(): String {
            // Comick requires an actually conformant PKCE process, unlike MAL
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
