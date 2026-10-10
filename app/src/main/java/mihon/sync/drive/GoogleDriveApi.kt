package mihon.sync.drive

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.sync.SyncLayout
import mihon.sync.auth.GoogleDriveAuth
import mihon.sync.auth.SyncAuthRequiredException
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.IOException
import kotlin.random.Random

/**
 * Minimal Google Drive v3 client over the app's existing OkHttp stack.
 *
 * Only what the sync needs: folders, listing, and reading/writing small files. Using the REST API
 * directly avoids `google-api-services-drive`, which would drag in a second HTTP stack.
 *
 * The `drive.file` scope means every listing here is already restricted to files this app created,
 * so no query can reach the user's other documents.
 */
@Inject
@SingleIn(AppScope::class)
class GoogleDriveApi(
    private val auth: GoogleDriveAuth,
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {

    suspend fun findFolder(name: String, parentId: String? = null): DriveFile? =
        findByName(name, parentId, folder = true)

    suspend fun createFolder(name: String, parentId: String? = null): DriveFile = withIOContext {
        val metadata = buildString {
            append("""{"name":${json.encodeToString(name)},"mimeType":"${SyncLayout.FOLDER_MIME}"""")
            if (parentId != null) append(""","parents":["$parentId"]""")
            append("}")
        }

        val url = "$API_BASE/files".toHttpUrl().newBuilder()
            .addQueryParameter("fields", FILE_FIELDS)
            .build()

        val body = metadata.toRequestBody(JSON_MEDIA_TYPE)
        val response = execute { token ->
            Request.Builder().url(url).headers(authHeaders(token)).post(body).build()
        }
        json.decodeFromString<DriveFile>(response)
    }

    suspend fun findOrCreateFolder(name: String, parentId: String? = null): DriveFile =
        findFolder(name, parentId) ?: createFolder(name, parentId)

    suspend fun findFile(name: String, parentId: String): DriveFile? =
        findByName(name, parentId, folder = false)

    /**
     * Every file directly inside [folderId], following pagination. Folders are excluded.
     */
    suspend fun listFolder(folderId: String): List<DriveFile> = withIOContext {
        val collected = mutableListOf<DriveFile>()
        var pageToken: String? = null

        do {
            val builder = "$API_BASE/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$folderId' in parents and trashed = false")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("fields", "nextPageToken, files($FILE_FIELDS)")
                .addQueryParameter("pageSize", "1000")
            if (pageToken != null) builder.addQueryParameter("pageToken", pageToken)

            val url = builder.build()
            val page = json.decodeFromString<DriveFileList>(
                execute { token -> Request.Builder().url(url).headers(authHeaders(token)).get().build() },
            )

            collected += page.files.filterNot { it.mimeType == SyncLayout.FOLDER_MIME }
            pageToken = page.nextPageToken
        } while (pageToken != null)

        collected
    }

    /**
     * Current metadata for one file, or null when it no longer exists.
     *
     * Used right before publishing a shard, to notice that another device wrote to it since this
     * one last merged — overwriting that blindly would drop their change.
     */
    suspend fun getMetadata(fileId: String): DriveFile? = withIOContext {
        val url = "$API_BASE/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", FILE_FIELDS)
            .build()

        val response = executeOrNullOnMissing { token ->
            Request.Builder().url(url).headers(authHeaders(token)).get().build()
        } ?: return@withIOContext null

        json.decodeFromString<DriveFile>(response)
    }

    /**
     * How full the account's Drive is, or null when Drive will not say.
     *
     * Allowed under `drive.file`: the quota is about the account, not about anyone's files, so it
     * discloses nothing this app could not already ask for.
     */
    suspend fun quota(): DriveQuota? = withIOContext {
        val url = "$API_BASE/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "storageQuota")
            .build()

        val response = executeOrNullOnMissing { token ->
            Request.Builder().url(url).headers(authHeaders(token)).get().build()
        } ?: return@withIOContext null

        json.decodeFromString<DriveAbout>(response).storageQuota
    }

    /**
     * Returns the raw payload, or null when the file no longer exists on the account.
     */
    suspend fun download(fileId: String): ByteArray? = withIOContext {
        val url = "$API_BASE/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()

        val response = rawExecute { token ->
            Request.Builder().url(url).headers(authHeaders(token)).get().build()
        }
        response.use {
            if (it.code == 404) return@withIOContext null
            it.ensureSuccessful("downloading a sync file")
            it.body.bytes()
        }
    }

    suspend fun downloadText(fileId: String): String? = download(fileId)?.toString(Charsets.UTF_8)

    suspend fun create(
        name: String,
        parentId: String,
        content: ByteArray,
        mimeType: String = BINARY_MIME,
    ): DriveFile = withIOContext {
        val metadata = """{"name":${json.encodeToString(name)},"parents":["$parentId"]}"""
        val body = MultipartBody.Builder()
            .setType(MULTIPART_RELATED)
            .addPart(metadata.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(content.toRequestBody(mimeType.toMediaType()))
            .build()

        val url = "$UPLOAD_BASE/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", FILE_FIELDS)
            .build()

        val response = execute { token ->
            Request.Builder().url(url).headers(authHeaders(token)).post(body).build()
        }
        json.decodeFromString<DriveFile>(response)
    }

    /**
     * Overwrites a file in place, keeping its id. Null when it was deleted remotely, so the caller
     * can recreate it.
     */
    suspend fun update(
        fileId: String,
        content: ByteArray,
        mimeType: String = BINARY_MIME,
    ): DriveFile? = withIOContext {
        val url = "$UPLOAD_BASE/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("fields", FILE_FIELDS)
            .build()

        val response = executeOrNullOnMissing { token ->
            Request.Builder()
                .url(url)
                .headers(authHeaders(token))
                .patch(content.toRequestBody(mimeType.toMediaType()))
                .build()
        } ?: return@withIOContext null

        json.decodeFromString<DriveFile>(response)
    }

    /**
     * Writes [content] to [name] inside [parentId], creating the file the first time.
     *
     * When [knownId] is gone, another copy by the same name is looked for before creating one: the
     * id may simply be stale, and a second file by that name is a duplicate every device would then
     * have to sort out.
     */
    suspend fun upsert(
        name: String,
        parentId: String,
        content: ByteArray,
        knownId: String? = null,
        mimeType: String = BINARY_MIME,
    ): DriveFile {
        if (knownId != null) {
            update(knownId, content, mimeType)?.let { return it }
        }
        val existing = findFile(name, parentId)?.takeIf { it.id != knownId }
        if (existing != null) {
            update(existing.id, content, mimeType)?.let { return it }
        }
        return create(name, parentId, content, mimeType)
    }

    /**
     * Removes a file for good. Nothing happens when it is already gone.
     */
    suspend fun delete(fileId: String): Unit = withIOContext {
        val url = "$API_BASE/files/$fileId".toHttpUrl()
        executeOrNullOnMissing { token ->
            Request.Builder().url(url).headers(authHeaders(token)).delete().build()
        }
    }

    private suspend fun findByName(name: String, parentId: String?, folder: Boolean): DriveFile? = withIOContext {
        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        val query = buildString {
            append("name = '$escaped' and trashed = false")
            append(if (folder) " and mimeType = '${SyncLayout.FOLDER_MIME}'" else "")
            if (parentId != null) append(" and '$parentId' in parents")
        }

        val url = "$API_BASE/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files($FILE_FIELDS)")
            .addQueryParameter("pageSize", "10")
            .build()

        json.decodeFromString<DriveFileList>(
            execute { token -> Request.Builder().url(url).headers(authHeaders(token)).get().build() },
        )
            .files
            // Two devices racing on a first sync can each create one; the newest is authoritative.
            .maxByOrNull { it.modifiedTime }
    }

    private fun authHeaders(token: String) = Headers.headersOf("Authorization", "Bearer $token")

    private suspend fun execute(buildRequest: (String) -> Request): String {
        return rawExecute(buildRequest).use {
            it.ensureSuccessful("calling Drive")
            it.body.string()
        }
    }

    private suspend fun executeOrNullOnMissing(buildRequest: (String) -> Request): String? {
        return rawExecute(buildRequest).use {
            if (it.code == 404) return null
            it.ensureSuccessful("calling Drive")
            it.body.string()
        }
    }

    /**
     * Runs the request, waiting and trying again when Drive is only briefly unavailable.
     *
     * Drive asks clients to back off and retry on request limits and server errors, and a round
     * moves several requests at once, so one such answer used to fail the whole round. A dropped
     * connection is retried too, except for a creation: the file may well exist already, and a
     * second attempt would make a duplicate.
     */
    private suspend fun rawExecute(buildRequest: (String) -> Request): Response {
        var attempt = 0
        while (true) {
            val response = try {
                authorizedCall(buildRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val isCreation = buildRequest("").method == "POST"
                if (isCreation || attempt >= MAX_RETRIES) throw e
                null
            }

            if (response != null) {
                if (attempt >= MAX_RETRIES || response.failure() != DriveFailure.Transient) return response
                response.close()
            }

            val wait = BASE_BACKOFF_MS * (1L shl attempt) + Random.nextLong(BASE_BACKOFF_MS)
            logcat(LogPriority.INFO) { "Drive is busy; trying again in ${wait}ms" }
            delay(wait)
            attempt++
        }
    }

    /**
     * Sends the request with a valid token, retrying once if Drive rejects it anyway — which happens
     * when access was revoked from the Google account page while a token was still nominally valid.
     */
    private suspend fun authorizedCall(buildRequest: (String) -> Request): Response {
        val response = networkHelper.client.newCall(buildRequest(auth.getValidAccessToken())).await()
        if (response.code != 401) return response

        response.close()
        auth.invalidateAccessToken()
        return networkHelper.client.newCall(buildRequest(auth.getValidAccessToken())).await()
    }

    private fun Response.failure(): DriveFailure? {
        if (isSuccessful) return null
        val reasons = runCatching {
            val body = peekBody(ERROR_BODY_LIMIT).string()
            json.decodeFromString<DriveErrorResponse>(body).error.errors.map { it.reason }
        }.getOrDefault(emptyList())
        return DriveFailure.of(code, reasons)
    }

    private fun Response.ensureSuccessful(action: String) {
        val failure = failure() ?: return

        val payload = peekBody(ERROR_BODY_LIMIT).string()
        val message = "Drive failed while $action (HTTP $code): $payload"
        throw when (failure) {
            DriveFailure.Unauthorized -> SyncAuthRequiredException(message)
            DriveFailure.StorageFull -> DriveStorageFullException(message)
            DriveFailure.Transient, DriveFailure.Other -> DriveException(message)
        }
    }

    companion object {
        private const val API_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"

        private const val FILE_FIELDS = "id,name,mimeType,version,modifiedTime,size,md5Checksum"
        private const val ERROR_BODY_LIMIT = 2048L

        /** Waits of about 1, 2, 4 and 8 seconds: enough to ride out a burst, short enough to wait for. */
        private const val MAX_RETRIES = 4
        private const val BASE_BACKOFF_MS = 1_000L

        const val TEXT_MIME = "text/plain"
        const val JSON_MIME = "application/json"
        private const val BINARY_MIME = "application/octet-stream"

        private val MULTIPART_RELATED = "multipart/related".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
