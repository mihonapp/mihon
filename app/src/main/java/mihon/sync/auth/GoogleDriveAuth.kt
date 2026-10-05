package mihon.sync.auth

import android.net.Uri
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.sync.SyncPreferences
import okhttp3.FormBody
import okhttp3.Headers
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Clock

/**
 * OAuth 2.0 with PKCE against Google, following the same hand-rolled approach the trackers use
 * (see `MyAnimeListApi`) rather than pulling in an auth library.
 *
 * Google only accepts a redirect whose scheme is the reversed client ID, so the redirect URI is
 * derived from [BuildConfig.GOOGLE_DRIVE_CLIENT_ID] and mirrored by a manifest placeholder in
 * `app/build.gradle.kts`.
 */
@Inject
@SingleIn(AppScope::class)
class GoogleDriveAuth(
    private val syncPreferences: SyncPreferences,
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {

    private val refreshMutex = Mutex()

    /**
     * False when no OAuth client ID was supplied at build time; the sync UI stays unavailable.
     */
    val isConfigured: Boolean get() = BuildConfig.GOOGLE_DRIVE_CLIENT_ID.isNotBlank()

    val isLoggedIn: Boolean get() = syncPreferences.refreshToken().get().isNotBlank()

    /**
     * Builds the consent URL and stores the PKCE verifier and CSRF state for [handleRedirect].
     */
    fun buildAuthorizationUrl(): Uri {
        check(isConfigured) { "No Google Drive OAuth client ID configured" }

        val codes = PkceUtil.generateS256Codes()
        val state = PkceUtil.generateCodeVerifier()

        syncPreferences.pendingCodeVerifier().set(codes.codeVerifier)
        syncPreferences.pendingAuthState().set(state)

        return AUTH_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", BuildConfig.GOOGLE_DRIVE_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "$DRIVE_SCOPE $EMAIL_SCOPE")
            .appendQueryParameter("code_challenge", codes.codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            // Required to receive a refresh token at all, and to receive one again on re-consent.
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
    }

    /**
     * Completes the flow from the redirect the browser sent back. Throws on any failure so the
     * caller can report it; partial state is always cleared.
     */
    suspend fun handleRedirect(uri: Uri) {
        val expectedState = syncPreferences.pendingAuthState().get()
        val codeVerifier = syncPreferences.pendingCodeVerifier().get()
        clearPendingAuth()

        val error = uri.getQueryParameter("error")
        if (error != null) {
            throw SyncAuthRequiredException("Google denied the authorization request: $error")
        }

        val state = uri.getQueryParameter("state")
        if (expectedState.isBlank() || state != expectedState) {
            throw SyncAuthRequiredException("Received an unexpected OAuth state back from Google")
        }

        val code = uri.getQueryParameter("code")
            ?: throw SyncAuthRequiredException("Google did not return an authorization code")

        val body = FormBody.Builder()
            .add("client_id", BuildConfig.GOOGLE_DRIVE_CLIENT_ID)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .build()

        val token = requestToken(body)
        val refreshToken = token.refreshToken
            ?: throw SyncAuthRequiredException(
                "Google did not return a refresh token; revoke the app's access and link it again",
            )

        val email = fetchAccountEmail(token.accessToken)
        val previous = syncPreferences.accountEmail().get()
        // Linking the same account again, after its grant expired, keeps everything this device knows
        // about the sync folder. A different account must not inherit it: its Drive holds none of it.
        if (previous.isNotBlank() && email.isNotBlank() && !previous.equals(email, ignoreCase = true)) {
            syncPreferences.clearRemoteState()
        }

        syncPreferences.refreshToken().set(refreshToken)
        storeAccessToken(token)
        if (email.isNotBlank() || previous.isBlank()) syncPreferences.accountEmail().set(email)
    }

    /**
     * Returns a usable access token, refreshing it when it is expired or about to be.
     */
    suspend fun getValidAccessToken(): String = refreshMutex.withLock {
        val expiresAt = syncPreferences.accessTokenExpiresAt().get()
        val current = syncPreferences.accessToken().get()
        if (current.isNotBlank() && Clock.System.now().toEpochMilliseconds() < expiresAt) {
            return@withLock current
        }

        val refreshToken = syncPreferences.refreshToken().get()
        if (refreshToken.isBlank()) {
            throw SyncAuthRequiredException("No Google account is linked")
        }

        val body = FormBody.Builder()
            .add("client_id", BuildConfig.GOOGLE_DRIVE_CLIENT_ID)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val token = try {
            requestToken(body)
        } catch (e: SyncAuthRequiredException) {
            // The grant is gone: revoked, or expired as it does every week while the app is in
            // testing. Only the tokens go. The account stays known, and so does what this device
            // knows about its Drive, so linking it again picks the sync up where it left off instead
            // of reading every entry again.
            forgetTokens()
            throw e
        }

        storeAccessToken(token)
        token.accessToken
    }

    /**
     * Forces the next [getValidAccessToken] to refresh. Called when Drive answers 401 even though
     * the token looked current, which happens after a remote revocation.
     */
    fun invalidateAccessToken() {
        syncPreferences.accessTokenExpiresAt().set(0L)
    }

    /**
     * Drops the tokens and nothing else, leaving the account to be linked again.
     */
    fun forgetTokens() {
        syncPreferences.accessToken().delete()
        syncPreferences.refreshToken().delete()
        syncPreferences.accessTokenExpiresAt().delete()
    }

    fun logout() {
        syncPreferences.accessToken().delete()
        syncPreferences.refreshToken().delete()
        syncPreferences.accessTokenExpiresAt().delete()
        syncPreferences.accountEmail().delete()
        // Folder ids and shard bookkeeping belong to the account that was just unlinked; keeping
        // them would make the next account inherit another one's remote layout.
        syncPreferences.clearRemoteState()
        clearPendingAuth()
    }

    private fun clearPendingAuth() {
        syncPreferences.pendingCodeVerifier().delete()
        syncPreferences.pendingAuthState().delete()
    }

    private suspend fun requestToken(body: FormBody): GoogleTokenResponse = withIOContext {
        val response = networkHelper.client.newCall(POST(TOKEN_URL, body = body)).await()
        response.use {
            if (!it.isSuccessful) {
                val payload = it.peekBody(ERROR_BODY_LIMIT).string()
                // 400/401 from the token endpoint means the grant itself is bad, never a transient error.
                if (it.code == 400 || it.code == 401) {
                    throw SyncAuthRequiredException("Google rejected the token request: $payload")
                }
                throw IllegalStateException("Google token request failed (HTTP ${it.code}): $payload")
            }
            with(json) { it.parseAs<GoogleTokenResponse>() }
        }
    }

    private fun storeAccessToken(token: GoogleTokenResponse) {
        syncPreferences.accessToken().set(token.accessToken)
        // Expire a minute early so a token can't die mid-request.
        val expiresAt = Clock.System.now().toEpochMilliseconds() +
            ((token.expiresIn - EXPIRY_MARGIN_SECONDS).coerceAtLeast(0) * 1000)
        syncPreferences.accessTokenExpiresAt().set(expiresAt)
    }

    /**
     * Best effort: the email is only shown in the settings, so a failure here must not fail login.
     */
    private suspend fun fetchAccountEmail(accessToken: String): String = withIOContext {
        try {
            val request = GET(USER_INFO_URL, headers = Headers.headersOf("Authorization", "Bearer $accessToken"))
            networkHelper.client.newCall(request).awaitSuccess().use {
                with(json) { it.parseAs<GoogleUserInfo>() }.email
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not read the Google account email" }
            ""
        }
    }

    private val redirectUri: String
        get() = "${reversedClientId()}:/oauth2redirect"

    private fun reversedClientId(): String {
        val prefix = BuildConfig.GOOGLE_DRIVE_CLIENT_ID.removeSuffix(CLIENT_ID_SUFFIX)
        return "com.googleusercontent.apps.$prefix"
    }

    companion object {
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"

        /**
         * Per-file access to files this app created. Non-sensitive, so it needs no Google review.
         */
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val EMAIL_SCOPE = "email"

        private const val CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"
        private const val EXPIRY_MARGIN_SECONDS = 60L
        private const val ERROR_BODY_LIMIT = 2048L
    }
}
