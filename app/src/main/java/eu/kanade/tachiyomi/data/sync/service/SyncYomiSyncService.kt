package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PATCH
import eu.kanade.tachiyomi.network.POST
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.gzip
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.sync.SyncPreferences
import java.util.concurrent.TimeUnit

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
) : SyncService(context, json, syncPreferences) {

    @Serializable
    enum class SyncStatus {
        @SerialName("pending")
        Pending,

        @SerialName("syncing")
        Syncing,

        @SerialName("success")
        Success,
    }

    @Serializable
    data class LockFile(
        @SerialName("id")
        val id: Int?,
        @SerialName("user_api_key")
        val userApiKey: String?,
        @SerialName("acquired_by")
        val acquiredBy: String?,
        @SerialName("last_synced")
        val lastSynced: String?,
        @SerialName("status")
        val status: SyncStatus,
        @SerialName("acquired_at")
        val acquiredAt: String?,
        @SerialName("expires_at")
        val expiresAt: String?,
    )

    @Serializable
    data class LockfileCreateRequest(
        @SerialName("acquired_by")
        val acquiredBy: String,
    )

    @Serializable
    data class LockfilePatchRequest(
        @SerialName("user_api_key")
        val userApiKey: String,
        @SerialName("acquired_by")
        val acquiredBy: String,
    )

    override suspend fun beforeSync() {
        val host = syncPreferences.syncHost().get()
        val apiKey = syncPreferences.syncAPIKey().get()
        val lockFileApi = "$host/api/sync/lock"
        val deviceId = syncPreferences.uniqueDeviceID()
        val client = OkHttpClient()
        val headers = Headers.Builder().add("X-API-Token", apiKey).build()
        val json = Json { ignoreUnknownKeys = true }

        val createLockfileRequest = LockfileCreateRequest(deviceId)
        val createLockfileJson = json.encodeToString(createLockfileRequest)

        val patchRequest = LockfilePatchRequest(apiKey, deviceId)
        val patchJson = json.encodeToString(patchRequest)

        val lockFileRequest = GET(
            url = lockFileApi,
            headers = headers,
        )

        val lockFileCreate = POST(
            url = lockFileApi,
            headers = headers,
            body = createLockfileJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
        )

        val lockFileUpdate = PATCH(
            url = lockFileApi,
            headers = headers,
            body = patchJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
        )

        // create lock file first
        client.newCall(lockFileCreate).execute()
        // update lock file acquired_by
        client.newCall(lockFileUpdate).execute()

        var backoff = 2000L // Start with 2 seconds
        val maxBackoff = 32000L // Maximum backoff time e.g., 32 seconds
        var lockFile: LockFile
        do {
            val response = client.newCall(lockFileRequest).execute()
            val responseBody = response.body.string()
            lockFile = json.decodeFromString<LockFile>(responseBody)
            logcat(LogPriority.DEBUG) { "SyncYomi lock file status: ${lockFile.status}" }

            if (lockFile.status != SyncStatus.Success) {
                logcat(LogPriority.DEBUG) { "Lock file not ready, retrying in $backoff ms..." }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
            }
        } while (lockFile.status != SyncStatus.Success)

        // update lock file acquired_by
        client.newCall(lockFileUpdate).execute()
    }

    override suspend fun pullSyncData(): SyncData? {
        val host = syncPreferences.syncHost().get()
        val apiKey = syncPreferences.syncAPIKey().get()
        val downloadUrl = "$host/api/sync/download"

        val client = OkHttpClient()
        val headers = Headers.Builder().add("X-API-Token", apiKey).build()

        val downloadRequest = GET(
            url = downloadUrl,
            headers = headers,
        )

        client.newCall(downloadRequest).execute().use { response ->
            val responseBody = response.body.string()

            if (response.isSuccessful) {
                return json.decodeFromString<SyncData>(responseBody)
            } else {
                notifier.showSyncError("Failed to download sync data: $responseBody")
                responseBody.let { logcat(LogPriority.ERROR) { "SyncError:$it" } }
                return null
            }
        }
    }

    override suspend fun pushSyncData(syncData: SyncData) {
        val host = syncPreferences.syncHost().get()
        val apiKey = syncPreferences.syncAPIKey().get()
        val uploadUrl = "$host/api/sync/upload"

        // Set timeout to 30 seconds
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val headers = Headers.Builder().add(
            "Content-Type",
            "application/gzip",
        ).add("Content-Encoding", "gzip").add("X-API-Token", apiKey).build()

        val mediaType = "application/gzip".toMediaTypeOrNull()

        val jsonData = json.encodeToString(syncData)
        val body = jsonData.toRequestBody(mediaType).gzip()

        val uploadRequest = POST(
            url = uploadUrl,
            headers = headers,
            body = body,
        )

        client.newCall(uploadRequest).execute().use {
            if (it.isSuccessful) {
                logcat(
                    LogPriority.DEBUG,
                ) { "SyncYomi sync completed!" }
            } else {
                val responseBody = it.body.string()
                notifier.showSyncError("Failed to upload sync data: $responseBody")
                responseBody.let { logcat(LogPriority.ERROR) { "SyncError:$it" } }
            }
        }
    }
}
