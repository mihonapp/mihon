package eu.kanade.tachiyomi.data.sync.service

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.sync.models.GoogleDriveFile
import eu.kanade.tachiyomi.data.sync.models.GoogleDriveFileList
import eu.kanade.tachiyomi.data.sync.models.GoogleDriveFileMetadata
import eu.kanade.tachiyomi.data.sync.models.GoogleDriveOAuthResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Minimal Google Drive client scoped to the application data folder, a hidden per-app folder in
 * the user's Drive. Only the sync snapshot lives there; no other Drive file is reachable.
 *
 * The OAuth client credentials come from the sync settings rather than from the build, so the app
 * can be configured entirely from the device.
 */
class GoogleDriveApi(
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val json: Json = Injekt.get(),
    baseClient: OkHttpClient = Injekt.get<NetworkHelper>().client,
) {

    // The snapshot can get large and Drive is not always fast
    private val client = baseClient.newBuilder().callTimeout(5.minutes).build()

    private val clientId: String
        get() = syncPreferences.googleDriveClientId.get().trim()

    private val clientSecret: String
        get() = syncPreferences.googleDriveClientSecret.get().trim()

    /**
     * Consent screen to send the user to. [redirectUri] is the loopback address the app listens on.
     */
    fun authUrl(redirectUri: String, codeChallenge: String): Uri = AUTH_URL.toUri().buildUpon()
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("redirect_uri", redirectUri)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("scope", SCOPE)
        .appendQueryParameter("code_challenge", codeChallenge)
        .appendQueryParameter("code_challenge_method", "S256")
        .appendQueryParameter("access_type", "offline")
        // Without this Google skips the refresh token on repeat sign-ins
        .appendQueryParameter("prompt", "consent")
        .build()

    /**
     * Exchanges the authorization code for a token pair and persists it. The refresh token is only
     * handed out once, hence the forced consent prompt.
     */
    suspend fun exchangeAuthorizationCode(
        authCode: String,
        codeVerifier: String,
        redirectUri: String,
    ) = withIOContext {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", authCode)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .build()

        val oauth = with(json) {
            client.newCall(POST(TOKEN_URL, body = body)).awaitSuccess().parseAs<GoogleDriveOAuthResponse>()
        }

        val refreshToken = oauth.refreshToken
            ?: throw SyncAuthException("Google did not return a refresh token, try signing in again")

        syncPreferences.googleDriveRefreshToken.set(refreshToken)
        storeAccessToken(oauth)
    }

    /**
     * Returns the snapshot stored in the app data folder, or null when nothing was synced yet.
     */
    suspend fun downloadSnapshot(): ByteArray? = withIOContext {
        val fileId = findSnapshotId() ?: return@withIOContext null

        client.newCall(GET("$DRIVE_URL/$fileId?alt=media", headers = authHeaders()))
            .awaitSuccess()
            .body
            .use { it.bytes() }
    }

    /**
     * Overwrites the snapshot in the app data folder, creating it if it does not exist yet.
     */
    suspend fun uploadSnapshot(content: ByteArray) = withIOContext {
        val fileId = findSnapshotId() ?: createSnapshotFile()

        val request = patch(
            url = "$UPLOAD_URL/$fileId?uploadType=media",
            headers = authHeaders(),
            body = content.toRequestBody(OCTET_STREAM),
        )
        client.newCall(request).awaitSuccess().close()
    }

    /**
     * Drops the remote snapshot so the next sync starts over from this device's library.
     */
    suspend fun deleteSnapshot() = withIOContext {
        val fileId = findSnapshotId() ?: return@withIOContext

        val request = Request.Builder()
            .url("$DRIVE_URL/$fileId")
            .delete()
            .headers(authHeaders())
            .build()
        client.newCall(request).awaitSuccess().close()

        syncPreferences.googleDriveFileId.delete()
    }

    private suspend fun findSnapshotId(): String? {
        syncPreferences.googleDriveFileId.get().takeIf { it.isNotBlank() }?.let { return it }

        val query = Uri.encode("name = '$SNAPSHOT_NAME' and trashed = false")
        val url = "$DRIVE_URL?spaces=appDataFolder&fields=files(id,name)&q=$query"

        val fileId = with(json) {
            client.newCall(GET(url, headers = authHeaders()))
                .awaitSuccess()
                .parseAs<GoogleDriveFileList>()
                .files
                .firstOrNull()
                ?.id
        }

        return fileId?.also { syncPreferences.googleDriveFileId.set(it) }
    }

    private suspend fun createSnapshotFile(): String {
        val metadata = json.encodeToString(
            GoogleDriveFileMetadata.serializer(),
            GoogleDriveFileMetadata(name = SNAPSHOT_NAME, parents = listOf(APP_DATA_FOLDER)),
        )

        val fileId = with(json) {
            client.newCall(POST(DRIVE_URL, headers = authHeaders(), body = metadata.toRequestBody(JSON_MEDIA_TYPE)))
                .awaitSuccess()
                .parseAs<GoogleDriveFile>()
                .id
        }

        syncPreferences.googleDriveFileId.set(fileId)
        return fileId
    }

    private fun patch(url: String, headers: Headers, body: RequestBody): Request = Request.Builder()
        .url(url)
        .patch(body)
        .headers(headers)
        .build()

    private suspend fun authHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer ${accessToken()}")
        .build()

    private suspend fun accessToken(): String {
        val cached = syncPreferences.googleDriveAccessToken.get()
        val expiry = syncPreferences.googleDriveTokenExpiry.get()
        if (cached.isNotBlank() && Clock.System.now().toEpochMilliseconds() < expiry) return cached

        return refreshAccessToken()
    }

    private suspend fun refreshAccessToken(): String {
        val refreshToken = syncPreferences.googleDriveRefreshToken.get()
        if (refreshToken.isBlank()) throw SyncAuthException("Not signed in to Google Drive")

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val oauth = try {
            with(json) {
                client.newCall(POST(TOKEN_URL, body = body)).awaitSuccess().parseAs<GoogleDriveOAuthResponse>()
            }
        } catch (e: Exception) {
            // A revoked or expired refresh token can only be recovered by signing in again
            syncPreferences.logoutGoogleDrive()
            throw SyncAuthException("Google Drive session expired, sign in again", e)
        }

        storeAccessToken(oauth)
        return oauth.accessToken
    }

    private fun storeAccessToken(oauth: GoogleDriveOAuthResponse) {
        syncPreferences.googleDriveAccessToken.set(oauth.accessToken)
        syncPreferences.googleDriveTokenExpiry.set(
            Clock.System.now().toEpochMilliseconds() + (oauth.expiresIn - EXPIRY_MARGIN_SECONDS) * 1000,
        )
    }

    companion object {
        const val SNAPSHOT_NAME = "mihon_sync.tachibk"

        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val DRIVE_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val EXPIRY_MARGIN_SECONDS = 60

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        fun generatePkceCodes() = PkceUtil.generateS256Codes()
    }
}

class SyncAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
