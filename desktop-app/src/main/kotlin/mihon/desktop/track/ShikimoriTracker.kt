package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val SHIKIMORI_URL = "https://shikimori.io"

class ShikimoriTracker(
    id: Long = 4L,
    baseUrl: String = SHIKIMORI_URL,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(id, "Shikimori", TrackerAuthType.TOKEN, "$SHIKIMORI_URL/oauth") {
    private val http = TrackerHttpClient(baseUrl, httpClient, requestTimeoutMillis)
    private var userId: String? = null

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val authToken = credentials["token"]?.trim().orEmpty()
        if (authToken.isBlank()) return false
        return try {
            val user = currentUser(authToken)
            userId = user.string("id")
            setLoggedIn(true, user.string("nickname"), authToken)
            !userId.isNullOrBlank()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            userId = null
            setLoggedIn(false)
            false
        }
    }

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.token.isBlank()) return
        userId = null
        setLoggedIn(true, info.username.takeIf(String::isNotBlank), info.token)
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val variables = buildJsonObject { put("query", query.trim()) }
        return graphQl(SEARCH_QUERY, variables).array("mangas").mapNotNull { (it as? JsonObject)?.toSearch() }
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        val variables = buildJsonObject { put("id", track.remoteId.toString()) }
        val manga = graphQl(FIND_QUERY, variables).array("mangas").firstOrNull() as? JsonObject ?: return null
        val rate = manga.obj("userRate") ?: return null
        return track.copy(
            libraryId = rate.string("id")?.toLongOrNull() ?: track.libraryId,
            title = manga.string("name") ?: track.title,
            totalChapters = manga.long("chapters") ?: track.totalChapters,
            lastChapterRead = rate.double("chapters") ?: track.lastChapterRead,
            score = rate.double("score") ?: track.score,
            status = rate.string("status")?.toTrackStatus() ?: track.status,
            trackingUrl = manga.string("url")?.let(::absoluteUrl) ?: track.trackingUrl,
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        requireLogin()
        val rate = buildJsonObject {
            put("chapters", track.lastChapterRead.toInt())
            put("score", track.score.toInt())
            put("status", TrackStatus.fromValue(track.status).toShikimoriStatus())
            if (track.libraryId <= 0L) {
                put("user_id", ensureUserId())
                put("target_id", track.remoteId)
                put("target_type", "Manga")
            }
        }
        val payload = buildJsonObject { put("user_rate", rate) }
        val path = if (track.libraryId > 0L) "/api/v2/user_rates/${track.libraryId}" else "/api/v2/user_rates"
        val builder = authenticated(path).method(
            if (track.libraryId > 0L) "PUT" else "POST",
            payload.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE),
        )
        val response = defaultTrackerJson.parseToJsonElement(http.execute(builder.build())).jsonObject
        return track.copy(libraryId = response.long("id") ?: track.libraryId)
    }

    private suspend fun currentUser(authToken: String = token.orEmpty()): JsonObject =
        graphQl(CURRENT_USER_QUERY, buildJsonObject { }, authToken).obj("currentUser")
            ?: throw TrackerApiException("Shikimori did not return the current user")

    private suspend fun ensureUserId(): String {
        userId?.let { return it }
        return currentUser().string("id")?.also { userId = it }
            ?: throw TrackerApiException("Shikimori did not return a user id")
    }

    private suspend fun graphQl(query: String, variables: JsonObject, authToken: String = token.orEmpty()): JsonObject {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val root = defaultTrackerJson.parseToJsonElement(
            http.execute(
                authenticated("/api/graphql", authToken).post(
                    payload.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE),
                ).build(),
            ),
        ).jsonObject
        if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) throw TrackerApiException("Shikimori API error")
        return root.obj("data") ?: throw TrackerApiException("Shikimori response did not contain data")
    }

    private fun authenticated(path: String, authToken: String = token.orEmpty()): Request.Builder = Request.Builder()
        .url(http.baseUrl + path)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer $authToken")

    private fun requireLogin() {
        if (!isLoggedIn || token.isNullOrBlank()) throw TrackerApiException("Not logged in to Shikimori")
    }

    private fun JsonObject.toSearch(): TrackSearchResult? {
        val mangaId = long("id") ?: return null
        val title = string("name")?.takeIf(String::isNotBlank) ?: return null
        return TrackSearchResult(
            id,
            mangaId,
            title,
            long("chapters") ?: 0L,
            obj("poster")?.string("mainUrl").orEmpty(),
            string("url")?.let(::absoluteUrl).orEmpty(),
            string("description").orEmpty(),
        )
    }

    private fun absoluteUrl(path: String): String = if (path.startsWith("http")) path else http.baseUrl + path

    private companion object {
        private const val CURRENT_USER_QUERY = "query { currentUser { id nickname } }"
        private const val SEARCH_QUERY =
            "query Search(\$query: String) { mangas(search: \$query, limit: 20, kind: \"!light_novel,!novel\") { " +
                "id name chapters poster { mainUrl } url description } }"
        private const val FIND_QUERY =
            "query Find(\$id: String) { mangas(ids: \$id, limit: 1) { id url name chapters " +
                "userRate { id chapters status score } } }"
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
private fun JsonObject.array(name: String): JsonArray = this[name]?.jsonArray ?: JsonArray(emptyList())
private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content
private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()
private fun JsonObject.double(name: String): Double? = string(name)?.toDoubleOrNull()
private fun String.toTrackStatus(): Long? = when (this) {
    "watching" -> TrackStatus.READING.value
    "completed" -> TrackStatus.COMPLETED.value
    "on_hold" -> TrackStatus.ON_HOLD.value
    "dropped" -> TrackStatus.DROPPED.value
    "planned" -> TrackStatus.PLAN_TO_READ.value
    "rewatching" -> TrackStatus.REREADING.value
    else -> null
}
private fun TrackStatus.toShikimoriStatus(): String = when (this) {
    TrackStatus.READING -> "watching"
    TrackStatus.COMPLETED -> "completed"
    TrackStatus.ON_HOLD -> "on_hold"
    TrackStatus.DROPPED -> "dropped"
    TrackStatus.PLAN_TO_READ -> "planned"
    TrackStatus.REREADING -> "rewatching"
}
