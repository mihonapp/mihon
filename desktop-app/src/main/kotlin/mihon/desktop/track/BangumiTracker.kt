package mihon.desktop.track

import kotlinx.coroutines.CancellationException
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

private const val BANGUMI_API_URL = "https://api.bgm.tv"

class BangumiTracker(
    id: Long = 5L,
    baseUrl: String = BANGUMI_API_URL,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(
    id,
    "Bangumi",
    TrackerAuthType.TOKEN,
    "https://next.bgm.tv/demo/access-token",
) {
    private val http = TrackerHttpClient(baseUrl, httpClient, requestTimeoutMillis)

    override val supportedStatuses = TrackStatus.entries.filterNot { it == TrackStatus.REREADING }

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val authToken = credentials["token"]?.trim().orEmpty()
        if (authToken.isBlank()) return false
        return try {
            val me = getJson("/v0/me", authToken)
            val account = me.bString("username") ?: throw TrackerApiException("Bangumi did not return an account")
            setLoggedIn(true, account, authToken)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            setLoggedIn(false)
            false
        }
    }

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.token.isBlank()) return
        setLoggedIn(true, info.username.takeIf(String::isNotBlank), info.token)
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        requireLogin()
        val body = buildJsonObject {
            put("keyword", query.trim())
            put("sort", "match")
            put("filter", buildJsonObject { put("type", buildJsonArray { add(JsonPrimitive(1)) }) })
        }
        val root = executeJson(
            authenticated("/v0/search/subjects?limit=20")
                .post(body.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE)).build(),
        )
        return root["data"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.bString("platform") in listOf(null, "漫画") }
            .mapNotNull { subject ->
                val remoteId = subject.bLong("id") ?: return@mapNotNull null
                val original = subject.bString("name").orEmpty()
                val translated = subject.bString("name_cn").orEmpty()
                val title = translated.ifBlank { original }.takeIf(String::isNotBlank) ?: return@mapNotNull null
                TrackSearchResult(
                    id,
                    remoteId,
                    title,
                    subject.bLong("eps") ?: 0L,
                    subject.bObj("images")?.bString("common").orEmpty(),
                    "https://bangumi.tv/subject/$remoteId",
                    subject.bString("summary").orEmpty(),
                )
            }
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        requireLogin()
        val account = username ?: getJson("/v0/me").bString("username")
            ?: throw TrackerApiException("Bangumi did not return an account")
        val collection = try {
            getJson("/v0/users/$account/collections/${track.remoteId}")
        } catch (error: TrackerHttpException) {
            if (error.code == 404) return null else throw error
        }
        return track.copy(
            libraryId = track.remoteId,
            lastChapterRead = collection.bDouble("ep_status") ?: 0.0,
            totalChapters = collection.bObj("subject")?.bLong("eps") ?: track.totalChapters,
            score = collection.bDouble("rate") ?: 0.0,
            status = collection.bLong("type")?.toBangumiTrackStatus() ?: track.status,
            private = collection.bString("private")?.toBooleanStrictOrNull() ?: track.private,
            trackingUrl = "https://bangumi.tv/subject/${track.remoteId}",
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        requireLogin()
        val body = buildJsonObject {
            put("type", TrackStatus.fromValue(track.status).toBangumiStatus())
            put("rate", track.score.toInt().coerceIn(0, 10))
            put("ep_status", track.lastChapterRead.toInt())
            put("private", track.private)
        }.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE)
        val builder = authenticated("/v0/users/-/collections/${track.remoteId}")
        val request = if (track.libraryId > 0L) builder.patch(body).build() else builder.post(body).build()
        http.execute(request)
        return track.copy(libraryId = track.remoteId, trackingUrl = "https://bangumi.tv/subject/${track.remoteId}")
    }

    private suspend fun getJson(path: String, authToken: String = token.orEmpty()): JsonObject =
        executeJson(authenticated(path, authToken).get().build())

    private suspend fun executeJson(request: Request): JsonObject {
        val text = http.execute(request)
        return if (text.isBlank()) JsonObject(emptyMap()) else defaultTrackerJson.parseToJsonElement(text).jsonObject
    }

    private fun authenticated(path: String, authToken: String = token.orEmpty()): Request.Builder = Request.Builder()
        .url(http.baseUrl + path)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer $authToken")

    private fun requireLogin() {
        if (!isLoggedIn || token.isNullOrBlank()) throw TrackerApiException("Not logged in to Bangumi")
    }
}

private fun JsonObject.bObj(name: String): JsonObject? = this[name] as? JsonObject
private fun JsonObject.bString(name: String): String? = this[name]?.jsonPrimitive?.content
private fun JsonObject.bLong(name: String): Long? = bString(name)?.toLongOrNull()
private fun JsonObject.bDouble(name: String): Double? = bString(name)?.toDoubleOrNull()
private fun Long.toBangumiTrackStatus(): Long? = when (this) {
    1L -> TrackStatus.PLAN_TO_READ.value
    2L -> TrackStatus.COMPLETED.value
    3L -> TrackStatus.READING.value
    4L -> TrackStatus.ON_HOLD.value
    5L -> TrackStatus.DROPPED.value
    else -> null
}
private fun TrackStatus.toBangumiStatus(): Int = when (this) {
    TrackStatus.PLAN_TO_READ -> 1
    TrackStatus.COMPLETED -> 2
    TrackStatus.READING, TrackStatus.REREADING -> 3
    TrackStatus.ON_HOLD -> 4
    TrackStatus.DROPPED -> 5
}
