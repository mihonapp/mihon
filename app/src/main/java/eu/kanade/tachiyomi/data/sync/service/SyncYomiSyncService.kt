package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupSerializer
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.HttpStatus
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,

    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    private class SyncYomiException(message: String?) : Exception(message)

    override suspend fun doSync(syncData: SyncData): Backup? {
        try {
            val (remoteData, etag) = pullSyncData()

            val finalSyncData = if (remoteData != null){
                assert(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
                logcat(LogPriority.DEBUG, "SyncService") {
                    "Try update remote data with ETag($etag)"
                }
                mergeSyncData(syncData, remoteData)
            } else {
                // init or overwrite remote data
                logcat(LogPriority.DEBUG) {
                    "Try overwrite remote data with ETag($etag)"
                }
                syncData
            }

            pushSyncData(finalSyncData, etag)
            return finalSyncData.backup

        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            notifier.showSyncError(e.message)
            return null
        }
    }

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        val lastETag = syncPreferences.lastSyncEtag().get()
        if (lastETag != "") {
            headersBuilder.add("If-None-Match", lastETag)
        }
        val headers = headersBuilder.build()

        val downloadRequest = GET(
            url = downloadUrl,
            headers = headers,
        )

        val client = OkHttpClient()
        val response = client.newCall(downloadRequest).await()

        if (response.code == HttpStatus.SC_NOT_MODIFIED) {
            // not modified
            assert(lastETag.isNotEmpty())
            logcat(LogPriority.INFO) {
                "Remote server not modified"
            }
            return Pair(null, lastETag)
        } else if (response.code == HttpStatus.SC_NOT_FOUND) {
            // maybe got deleted from remote
            return Pair(null, "")
        }

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")

            val byteArray = response.body.byteStream().use {
                return@use it.readBytes()
            }

            return try {
                val backup = protoBuf.decodeFromByteArray(BackupSerializer, byteArray)
                return Pair(SyncData(backup = backup), newETag)
            } catch (_: SerializationException) {
                logcat(LogPriority.INFO) {
                    "Bad content responsed from server"
                }
                // the body is invalid
                // return default value so we can overwrite it
                Pair(null, "")
            }

        } else {
            val responseBody = response.body.string()
            notifier.showSyncError("Failed to download sync data: $responseBody")
            logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
            throw SyncYomiException("Failed to download sync data: $responseBody")
        }
    }

    /**
     * Return true if update success
     */
    private suspend fun pushSyncData(syncData: SyncData, eTag: String) {
        val backup = syncData.backup ?: return

        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val uploadUrl = "$host/api/sync/content"
        val timeout = 30L

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        // Set timeout to 30 seconds
        val client = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()

        val byteArray = protoBuf.encodeToByteArray(BackupSerializer, backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
        }
        val body = byteArray.toRequestBody("application/octet-stream".toMediaType())

        val uploadRequest = PUT(
            url = uploadUrl,
            headers = headers,
            body = body,
        )

        val response = client.newCall(uploadRequest).await()

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")
            syncPreferences.lastSyncEtag().set(newETag)
            logcat(LogPriority.DEBUG) { "SyncYomi sync completed" }

        } else if (response.code == HttpStatus.SC_PRECONDITION_FAILED) {
            // other clients updated remote data, will try next time
            logcat(LogPriority.DEBUG) { "SyncYomi sync failed with 412" }

        } else {
            val responseBody = response.body.string()
            notifier.showSyncError("Failed to upload sync data: $responseBody")
            logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
        }
    }
}
