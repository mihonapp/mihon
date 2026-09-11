package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Komga server tracker. The server URL is retained in [DesktopTrackRecord.trackingUrl], because
 * Komga series IDs are UUID strings while the shared desktop record retains a numeric list key.
 */
class KomgaTracker(
    id: Long = 6L,
    private val httpClient: OkHttpClient = defaultTrackerHttpClient(),
    private val requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(id, "Komga", TrackerAuthType.SERVER) {
    private var baseUrl: String? = null

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val url = (credentials["server_url"] ?: credentials["url"]).orEmpty().trim().trimEnd('/')
        val credential = (credentials["token"] ?: credentials["password"]).orEmpty().trim()
        val username = credentials["username"]?.trim().orEmpty()
        if (url.isBlank() || credential.isBlank()) return false

        return try {
            val http = TrackerHttpClient(url, httpClient, requestTimeoutMillis)
            http.execute(authenticatedRequest(http, "/api/v1/libraries", credential, username).build())
            baseUrl = http.baseUrl
            setLoggedIn(true, username.takeIf { it.isNotBlank() }, credential, http.baseUrl)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            setLoggedIn(false)
            false
        }
    }

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.serverUrl.isBlank() || info.token.isBlank()) return
        baseUrl = info.serverUrl.trim().trimEnd('/')
        setLoggedIn(
            true,
            info.username.takeIf { it.isNotBlank() },
            info.token,
            baseUrl,
        )
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val http = requireClient()
        val payload = buildJsonObject { put("fullTextSearch", query.trim()) }
        val request = authenticatedRequest(http, "/api/v1/series/list?page=0&size=50")
            .post(payload.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE))
            .build()
        val root = defaultTrackerJson.parseToJsonElement(http.execute(request)).jsonObject
        return root["content"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull(::toSearchResult)
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        val http = requireClient()
        val seriesId = seriesIdFrom(track) ?: return null
        val series = getSeries(http, seriesId) ?: return null
        val progress = getProgress(http, seriesId)
        return track.copy(
            remoteId = stableRemoteId(seriesId),
            title = series.metadata?.title?.takeIf { it.isNotBlank() } ?: series.name,
            totalChapters = progress.maxNumberSort.toLong(),
            lastChapterRead = progress.lastReadContinuousNumberSort,
            status = progress.toTrackStatus(),
            trackingUrl = seriesUrl(http.baseUrl, seriesId),
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        val http = requireClient()
        val seriesId = seriesIdFrom(track)
            ?: throw TrackerApiException("Komga tracking URL does not identify a series")
        val payload = buildJsonObject { put("lastBookNumberSortRead", track.lastChapterRead) }
        val request = authenticatedRequest(http, "/api/v2/series/$seriesId/read-progress/tachiyomi")
            .put(payload.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE))
            .build()
        http.execute(request)
        return findRemote(track) ?: track
    }

    private suspend fun getSeries(http: TrackerHttpClient, seriesId: String): KomgaSeries? {
        val request = authenticatedRequest(http, "/api/v1/series/$seriesId").build()
        return defaultTrackerJson.decodeFromString(KomgaSeries.serializer(), http.execute(request))
    }

    private suspend fun getProgress(http: TrackerHttpClient, seriesId: String): KomgaReadProgress {
        val request = authenticatedRequest(http, "/api/v2/series/$seriesId/read-progress/tachiyomi").build()
        return defaultTrackerJson.decodeFromString(KomgaReadProgress.serializer(), http.execute(request))
    }

    private fun requireClient(): TrackerHttpClient {
        val url = baseUrl ?: throw TrackerApiException("Not logged in to Komga")
        if (!isLoggedIn || token.isNullOrBlank()) throw TrackerApiException("Not logged in to Komga")
        return TrackerHttpClient(url, httpClient, requestTimeoutMillis)
    }

    private fun authenticatedRequest(
        http: TrackerHttpClient,
        path: String,
        credential: String = token.orEmpty(),
        user: String = username.orEmpty(),
    ): Request.Builder = Request.Builder()
        .url(http.baseUrl + path)
        .header("Accept", "application/json")
        .apply {
            if (user.isBlank()) {
                header(
                    "X-API-Key",
                    credential,
                )
            } else {
                header("Authorization", Credentials.basic(user, credential))
            }
        }

    private fun seriesIdFrom(track: DesktopTrackRecord): String? {
        val url = track.trackingUrl
        val marker = "/api/v1/series/"
        return url.substringAfter(marker, missingDelimiterValue = "")
            .substringBefore('/')
            .takeIf { it.isNotBlank() }
    }

    private fun toSearchResult(series: JsonObject): TrackSearchResult? {
        val seriesId = series["id"]?.jsonPrimitive?.content.orEmpty()
        if (seriesId.isBlank()) return null
        val metadata = series["metadata"]?.jsonObject
        val title = metadata?.get("title")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: series["name"]?.jsonPrimitive?.content.orEmpty()
        if (title.isBlank()) return null
        val server = baseUrl ?: return null
        return TrackSearchResult(
            trackerId = id,
            remoteId = stableRemoteId(seriesId),
            title = title,
            totalChapters = series["booksCount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            coverUrl = "${seriesUrl(server, seriesId)}/thumbnail",
            trackingUrl = seriesUrl(server, seriesId),
            summary = metadata?.get("summary")?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun seriesUrl(server: String, seriesId: String): String = "$server/api/v1/series/$seriesId"

    private fun stableRemoteId(seriesId: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest(seriesId.toByteArray())
        return ByteBuffer.wrap(bytes, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE).coerceAtLeast(1L)
    }
}

@Serializable
private data class KomgaSeries(
    val id: String,
    val name: String,
    val metadata: KomgaSeriesMetadata? = null,
)

@Serializable
private data class KomgaSeriesMetadata(
    val title: String? = null,
)

@Serializable
private data class KomgaReadProgress(
    val booksCount: Int = 0,
    val booksReadCount: Int = 0,
    val booksUnreadCount: Int = 0,
    val booksInProgressCount: Int = 0,
    val lastReadContinuousNumberSort: Double = 0.0,
    val maxNumberSort: Double = 0.0,
) {
    fun toTrackStatus(): Long = when {
        booksCount > 0 && booksReadCount == booksCount -> TrackStatus.COMPLETED.value
        booksCount > 0 && booksUnreadCount == booksCount -> TrackStatus.PLAN_TO_READ.value
        else -> TrackStatus.READING.value
    }
}
