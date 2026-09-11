package mihon.desktop.track

import com.sun.net.httpserver.HttpExchange
import okhttp3.OkHttpClient
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

internal data class TrackerTestRecordedRequest(
    val method: String,
    val path: String,
    val query: String?,
    val body: String,
    val authorization: String?,
    val apiKey: String?,
    val contentType: String?,
)

internal fun HttpExchange.recordTrackerTestRequest(): TrackerTestRecordedRequest {
    val body = requestBody.use { it.readBytes().decodeToString() }
    return TrackerTestRecordedRequest(
        method = requestMethod,
        path = requestURI.path,
        query = requestURI.rawQuery,
        body = body,
        authorization = requestHeaders.getFirst("Authorization"),
        apiKey = requestHeaders.getFirst("X-API-Key"),
        contentType = requestHeaders.getFirst("Content-Type"),
    )
}

internal fun HttpExchange.respondTrackerTestJson(body: String, status: Int = 200) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

internal fun parseTrackerTestFormBody(body: String): Map<String, String> = body
    .split('&')
    .filter { it.isNotBlank() }
    .associate { pair ->
        val separator = pair.indexOf('=')
        val key = if (separator >= 0) pair.substring(0, separator) else pair
        val value = if (separator >= 0) pair.substring(separator + 1) else ""
        URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
    }

internal fun trackerTestHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(5, TimeUnit.SECONDS)
    .build()

/**
 * In-memory tracker used by manager/offline-queue tests so those tests do not depend on HTTP.
 */
internal class TrackerTestFakeTracker(
    override val id: Long,
    override val name: String = "Fake Tracker $id",
    var loginSucceeds: Boolean = true,
    var updateSucceeds: Boolean = true,
) : BaseDesktopTracker(id, name) {
    val updates = mutableListOf<DesktopTrackRecord>()

    override suspend fun login(credentials: Map<String, String>): Boolean {
        if (!loginSucceeds) return false
        setLoggedIn(
            value = true,
            user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "FakeUser",
            authToken = credentials["token"] ?: credentials["password"] ?: "fake-token",
        )
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> = emptyList()

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        if (!updateSucceeds) throw TrackerApiException("Fake update failure")
        updates += track
        return track
    }
}
