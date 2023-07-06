package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.data.sync.models.SData
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
    override suspend fun doSync(syncData: SData): Backup? {
        logcat(
            LogPriority.DEBUG,
        ) { "SyncYomi sync started!" }

        val jsonData = json.encodeToString(syncData)

        val host = syncPreferences.syncHost().get()
        val apiKey = syncPreferences.syncAPIKey().get()
        val url = "$host/api/sync/data"

        val client = OkHttpClient()
        val mediaType = "application/gzip".toMediaTypeOrNull()
        val body = jsonData.toRequestBody(mediaType).gzip()

        val headers = Headers.Builder().add("Content-Type", "application/gzip").add("Content-Encoding", "gzip").add("X-API-Token", apiKey).build()

        val request = POST(
            url = url,
            headers = headers,
            body = body,
        )

        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()

            if (response.isSuccessful) {
                val syncDataResponse: SData = json.decodeFromString(responseBody)

                // If the device ID is 0 and not equal to the server device ID (this happens when the DB is fresh and the app is not), update it
                if (syncPreferences.deviceID().get() == 0 || syncPreferences.deviceID().get() != syncDataResponse.device?.id) {
                    syncDataResponse.device?.id?.let { syncPreferences.deviceID().set(it) }
                }

                logcat(
                    LogPriority.DEBUG,
                ) { "SyncYomi sync completed!" }

                return decodeSyncBackup(responseBody)
            } else {
                notifier.showSyncError("Failed to sync: $responseBody")
                responseBody.let { logcat(LogPriority.ERROR) { "SyncError:$it" } }
                return null
            }
        }
    }
}
