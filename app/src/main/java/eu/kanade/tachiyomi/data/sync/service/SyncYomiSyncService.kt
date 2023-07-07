package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.data.sync.models.SyncData
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
) : SyncService(context, json, syncPreferences) {
    override suspend fun pushSyncData(): SyncData? {
        val host = syncPreferences.syncHost().get()
        val apiKey = syncPreferences.syncAPIKey().get()
        val deviceId = syncPreferences.deviceID().get()
        val downloadUrl = "$host/api/sync/download?deviceId=$deviceId"

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

    override suspend fun pullSyncData(syncData: SyncData) {
        val host = syncPreferences.syncHost().get()
        val apiKey = syncPreferences.syncAPIKey().get()
        val uploadUrl = "$host/api/sync/upload"

        val client = OkHttpClient()

        val headers = Headers.Builder().add("Content-Type", "application/gzip").add("Content-Encoding", "gzip").add("X-API-Token", apiKey).build()

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
