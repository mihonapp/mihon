package eu.kanade.tachiyomi.data.sync.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import eu.kanade.tachiyomi.data.sync.models.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.sync.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class GoogleDriveSyncService(context: Context, json: Json, syncPreferences: SyncPreferences) : SyncService(context, json, syncPreferences) {
    constructor(context: Context) : this(
        context,
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        },
        Injekt.get<SyncPreferences>(),
    )

    private val remoteFileName = "tachiyomi_sync_data.gz"

    private val googleDriveService = GoogleDriveService(context)

    override suspend fun beforeSync() = googleDriveService.refreshToken()

    override suspend fun pushSyncData(): SyncData? {
        val drive = googleDriveService.googleDriveService

        // Check if the Google Drive service is initialized
        if (drive == null) {
            logcat(LogPriority.ERROR) { "Google Drive service not initialized" }
            return null
        }

        val fileList = getFileList(drive)

        if (fileList.isEmpty()) {
            return null
        }
        val gdriveFileId = fileList[0].id

        val outputStream = ByteArrayOutputStream()
        drive.files().get(gdriveFileId).executeMediaAndDownloadTo(outputStream)
        val jsonString = withContext(Dispatchers.IO) {
            val gzipInputStream = GZIPInputStream(ByteArrayInputStream(outputStream.toByteArray()))
            gzipInputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        return json.decodeFromString(SyncData.serializer(), jsonString)
    }

    override suspend fun pullSyncData(syncData: SyncData) {
        val jsonData = json.encodeToString(syncData)

        val drive = googleDriveService.googleDriveService

        // Check if the Google Drive service is initialized
        if (drive == null) {
            logcat(LogPriority.ERROR) { "Google Drive service not initialized" }
            return
        }

        // delete file if exists
        val fileList = getFileList(drive)
        if (fileList.isNotEmpty()) {
            drive.files().delete(fileList[0].id).execute()
        }

        val fileMetadata = File()
        fileMetadata.name = remoteFileName
        fileMetadata.mimeType = "application/gzip"

        val byteArrayOutputStream = ByteArrayOutputStream()

        withContext(Dispatchers.IO) {
            val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)
            gzipOutputStream.write(jsonData.toByteArray(Charsets.UTF_8))
            gzipOutputStream.close()
        }

        val byteArrayContent = ByteArrayContent("application/octet-stream", byteArrayOutputStream.toByteArray())
        val uploadedFile = drive.files().create(fileMetadata, byteArrayContent)
            .setFields("id")
            .execute()

        logcat(LogPriority.DEBUG) { "Created sync data file in Google Drive with file ID: ${uploadedFile.id}" }
    }

    private fun getFileList(drive: Drive): MutableList<File> {
        // Search for the existing file by name
        val query = "mimeType='application/gzip' and trashed = false and name = '$remoteFileName'"
        val fileList = drive.files().list().setQ(query).execute().files
        Log.d("GoogleDrive", "File list: $fileList")

        return fileList
    }

    suspend fun deleteSyncDataFromGoogleDrive(): Boolean {
        val drive = googleDriveService.googleDriveService

        if (drive == null) {
            logcat(LogPriority.ERROR) { "Google Drive service not initialized" }
            return false
        }
        googleDriveService.refreshToken()

        return withContext(Dispatchers.IO) {
            val query = "mimeType='application/gzip' and trashed = false and name = '$remoteFileName'"
            val fileList = drive.files().list().setQ(query).execute().files

            if (fileList.isNullOrEmpty()) {
                logcat(LogPriority.DEBUG) { "No sync data file found in Google Drive" }
                false
            } else {
                val fileId = fileList[0].id
                drive.files().delete(fileId).execute()
                logcat(LogPriority.DEBUG) { "Deleted sync data file in Google Drive with file ID: $fileId" }
                true
            }
        }
    }
}

class GoogleDriveService(private val context: Context) {
    var googleDriveService: Drive? = null
    private val syncPreferences = Injekt.get<SyncPreferences>()

    init {
        initGoogleDriveService()
    }

    /**
     * Initializes the Google Drive service by obtaining the access token and refresh token from the SyncPreferences
     * and setting up the service using the obtained tokens.
     */
    private fun initGoogleDriveService() {
        val accessToken = syncPreferences.getGoogleDriveAccessToken()
        val refreshToken = syncPreferences.getGoogleDriveRefreshToken()

        if (accessToken == "" || refreshToken == "") {
            googleDriveService = null
            return
        }

        setupGoogleDriveService(accessToken, refreshToken)
    }

    /**
     * Launches an Intent to open the user's default browser for Google Drive sign-in.
     * The Intent carries the authorization URL, which prompts the user to sign in
     * and grant the application permission to access their Google Drive account.
     * @return An Intent configured to launch a browser for Google Drive OAuth sign-in.
     */
    fun getSignInIntent(): Intent {
        val authorizationUrl = generateAuthorizationUrl()

        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(authorizationUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Generates the authorization URL required for the user to grant the application permission to access their Google Drive account.
     * Sets the approval prompt to "force" to ensure that the user is always prompted to grant access, even if they have previously granted access.
     * @return The authorization URL.
     */
    private fun generateAuthorizationUrl(): String {
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val secrets = GoogleClientSecrets.load(
            jsonFactory,
            InputStreamReader(context.assets.open("client_secrets.json")),
        )

        val flow = GoogleAuthorizationCodeFlow.Builder(
            NetHttpTransport(),
            jsonFactory,
            secrets,
            listOf(DriveScopes.DRIVE_FILE),
        ).setAccessType("offline").build()

        return flow.newAuthorizationUrl()
            .setRedirectUri("eu.kanade.google.oauth:/oauth2redirect")
            .setApprovalPrompt("force")
            .build()
    }
    internal suspend fun refreshToken() = withContext(Dispatchers.IO) {
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val secrets = GoogleClientSecrets.load(
            jsonFactory,
            InputStreamReader(context.assets.open("client_secrets.json")),
        )

        val credential = GoogleCredential.Builder()
            .setJsonFactory(jsonFactory)
            .setTransport(NetHttpTransport())
            .setClientSecrets(secrets)
            .build()

        credential.refreshToken = syncPreferences.getGoogleDriveRefreshToken()

        logcat(LogPriority.DEBUG) { "Refreshing access token with: ${syncPreferences.getGoogleDriveRefreshToken()}" }

        try {
            credential.refreshToken()
            val newAccessToken = credential.accessToken
            val oldAccessToken = syncPreferences.getGoogleDriveAccessToken()
            // Save the new access token
            syncPreferences.setGoogleDriveAccessToken(newAccessToken)
            setupGoogleDriveService(newAccessToken, credential.refreshToken)
            logcat(LogPriority.DEBUG) { "Google Access token refreshed old: $oldAccessToken new: $newAccessToken" }
        } catch (e: TokenResponseException) {
            if (e.details.error == "invalid_grant") {
                // The refresh token is invalid, prompt the user to sign in again
                logcat(LogPriority.ERROR) { "Refresh token is invalid, prompt user to sign in again" }
                throw e.message?.let { Exception(it) } ?: Exception("Unknown error")
            } else {
                // Token refresh failed; handle this situation
                logcat(LogPriority.ERROR) { "Failed to refresh access token ${e.message}" }
                logcat(LogPriority.ERROR) { "Google Drive sync will be disabled" }
            }
        } catch (e: IOException) {
            // Token refresh failed; handle this situation
            logcat(LogPriority.ERROR) { "Failed to refresh access token ${e.message}" }
            logcat(LogPriority.ERROR) { "Google Drive sync will be disabled" }
        }
    }

    /**
     * Sets up the Google Drive service using the provided access token and refresh token.
     * @param accessToken The access token obtained from the SyncPreferences.
     * @param refreshToken The refresh token obtained from the SyncPreferences.
     */
    private fun setupGoogleDriveService(accessToken: String, refreshToken: String) {
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val secrets = GoogleClientSecrets.load(
            jsonFactory,
            InputStreamReader(context.assets.open("client_secrets.json")),
        )

        val credential = GoogleCredential.Builder()
            .setJsonFactory(jsonFactory)
            .setTransport(NetHttpTransport())
            .setClientSecrets(secrets)
            .build()

        credential.accessToken = accessToken
        credential.refreshToken = refreshToken

        googleDriveService = Drive.Builder(
            NetHttpTransport(),
            jsonFactory,
            credential,
        ).setApplicationName("Tachiyomi")
            .build()
    }

    /**
     * Handles the authorization code returned after the user has granted the application permission to access their Google Drive account.
     * It obtains the access token and refresh token using the authorization code, saves the tokens to the SyncPreferences,
     * sets up the Google Drive service using the obtained tokens, and initializes the service.
     * @param authorizationCode The authorization code obtained from the OAuthCallbackServer.
     * @param activity The current activity.
     * @param onSuccess A callback function to be called on successful authorization.
     * @param onFailure A callback function to be called on authorization failure.
     */
    fun handleAuthorizationCode(authorizationCode: String, activity: Activity, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val secrets = GoogleClientSecrets.load(
            jsonFactory,
            InputStreamReader(context.assets.open("client_secrets.json")),
        )

        val tokenResponse: GoogleTokenResponse = GoogleAuthorizationCodeTokenRequest(
            NetHttpTransport(),
            jsonFactory,
            secrets.installed.clientId,
            secrets.installed.clientSecret,
            authorizationCode,
            "eu.kanade.google.oauth:/oauth2redirect",
        ).setGrantType("authorization_code").execute()

        try {
            // Save the access token and refresh token
            val accessToken = tokenResponse.accessToken
            val refreshToken = tokenResponse.refreshToken

            // Save the tokens to SyncPreferences
            syncPreferences.setGoogleDriveAccessToken(accessToken)
            syncPreferences.setGoogleDriveRefreshToken(refreshToken)

            setupGoogleDriveService(accessToken, refreshToken)
            initGoogleDriveService()

            activity.runOnUiThread {
                onSuccess()
            }
        } catch (e: Exception) {
            activity.runOnUiThread {
                onFailure(e.localizedMessage ?: "Unknown error")
            }
        }
    }
}
