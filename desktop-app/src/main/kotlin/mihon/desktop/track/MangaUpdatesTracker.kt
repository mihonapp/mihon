@file:Suppress("ktlint:standard:max-line-length")

package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MangaUpdatesTracker(
    baseUrl: String = "https://api.mangaupdates.com",
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    id: Long = 7L,
    requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(id, "MangaUpdates", TrackerAuthType.CREDENTIALS) {
    private val http = TrackerHttpClient(baseUrl, httpClient, requestTimeoutMillis)
    override val supportedStatuses = TrackStatus.entries.filterNot { it == TrackStatus.REREADING }

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val user = credentials["username"]?.trim().orEmpty()
        val password = credentials["password"].orEmpty()
        if (user.isBlank() || password.isBlank()) return false
        return try {
            val login = jsonRequest(
                "/v1/account/login",
                "PUT",
                buildJsonObject {
                    put("username", user)
                    put("password", password)
                },
                authenticated = false,
            )
            val session = login.muObj("context")?.muString("session_token")
                ?: throw TrackerApiException("MangaUpdates did not return a session")
            val profile = getJson("/v1/account/profile", session)
            setLoggedIn(true, profile.muString("username") ?: user, session)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            setLoggedIn(false)
            false
        }
    }

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.token.isNotBlank()) setLoggedIn(true, info.username.takeIf(String::isNotBlank), info.token)
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val root = jsonRequest(
            "/v1/series/search",
            "POST",
            buildJsonObject {
                put("search", query.trim())
                put(
                    "filter_types",
                    buildJsonArray {
                        add(JsonPrimitive("drama cd"))
                        add(JsonPrimitive("novel"))
                    },
                )
            },
            authenticated = false,
        )
        return root["results"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val record = (entry as? JsonObject)?.muObj("record") ?: return@mapNotNull null
            val remoteId = record.muLong("series_id") ?: return@mapNotNull null
            val title = record.muString("title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            TrackSearchResult(
                id,
                remoteId,
                title,
                record.muLong("latest_chapter") ?: 0L,
                record.muObj("image")?.muObj("url")?.muString("original").orEmpty(),
                record.muString("url").orEmpty(),
                record.muString("description").orEmpty(),
            )
        }
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        requireLogin()
        val item = try {
            getJson("/v1/lists/series/${track.remoteId}")
        } catch (e: TrackerHttpException) {
            if (e.code == 404) return null else throw e
        }
        val rating = runCatching { getJson("/v1/series/${track.remoteId}/rating").muDouble("rating") }.getOrNull()
        return track.copy(
            libraryId = track.remoteId,
            lastChapterRead = item.muObj("status")?.muDouble("chapter") ?: 0.0,
            score = rating ?: 0.0,
            status = item.muLong("list_id")?.toDesktopStatus() ?: track.status,
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        requireLogin()
        val entry = buildJsonObject {
            put("series", buildJsonObject { put("id", track.remoteId) })
            put("list_id", TrackStatus.fromValue(track.status).toListId())
            put("status", buildJsonObject { put("chapter", track.lastChapterRead.toInt()) })
        }
        jsonRequest(
            if (track.libraryId > 0L) "/v1/lists/series/update" else "/v1/lists/series",
            "POST",
            JsonArray(listOf(entry)),
        )
        if (track.score > 0) {
            jsonRequest(
                "/v1/series/${track.remoteId}/rating",
                "PUT",
                buildJsonObject { put("rating", track.score) },
            )
        }
        return track.copy(libraryId = track.remoteId)
    }

    private suspend fun getJson(path: String, authToken: String = token.orEmpty()) = execute(
        Request.Builder().url(http.baseUrl + path).header("Authorization", "Bearer $authToken").get().build(),
    )

    private suspend fun jsonRequest(path: String, method: String, body: kotlinx.serialization.json.JsonElement, authenticated: Boolean = true): JsonObject {
        val builder = Request.Builder().url(http.baseUrl + path)
        if (authenticated) builder.header("Authorization", "Bearer ${token.orEmpty()}")
        return execute(builder.method(method, body.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE)).build())
    }

    private suspend fun execute(request: Request): JsonObject {
        val text = http.execute(request)
        return if (text.isBlank()) JsonObject(emptyMap()) else defaultTrackerJson.parseToJsonElement(text).jsonObject
    }
    private fun requireLogin() {
        if (!isLoggedIn ||
            token.isNullOrBlank()
        ) {
            throw TrackerApiException("Not logged in to MangaUpdates")
        }
    }
}

private fun JsonObject.muObj(name: String) = this[name] as? JsonObject
private fun JsonObject.muString(name: String) = this[name]?.jsonPrimitive?.content
private fun JsonObject.muLong(name: String) = muString(name)?.toLongOrNull()
private fun JsonObject.muDouble(name: String) = muString(name)?.toDoubleOrNull()
private fun Long.toDesktopStatus() = when (this) {
    0L -> TrackStatus.READING.value
    1L -> TrackStatus.PLAN_TO_READ.value
    2L -> TrackStatus.COMPLETED.value
    3L -> TrackStatus.DROPPED.value
    4L -> TrackStatus.ON_HOLD.value
    else -> TrackStatus.READING.value
}
private fun TrackStatus.toListId() = when (this) {
    TrackStatus.READING, TrackStatus.REREADING -> 0
    TrackStatus.PLAN_TO_READ -> 1
    TrackStatus.COMPLETED -> 2
    TrackStatus.DROPPED -> 3
    TrackStatus.ON_HOLD -> 4
}
