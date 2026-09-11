package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

private const val KITSU_GRAPHQL_URL = "https://kitsu.app/api/graphql"
private const val KITSU_OAUTH_URL = "https://kitsu.app/api/oauth/token"
private const val KITSU_MANGA_URL = "https://kitsu.app/manga/"

/**
 * Kitsu tracker backed by the public OAuth and GraphQL endpoints.  Kitsu's password grant is
 * exchanged immediately for an access token; the manager persists that token, never the password.
 */
class KitsuTracker(
    id: Long = 3L,
    graphQlUrl: String = KITSU_GRAPHQL_URL,
    oauthUrl: String = KITSU_OAUTH_URL,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(id, "Kitsu", TrackerAuthType.CREDENTIALS) {
    private val graphql = TrackerHttpClient(graphQlUrl, httpClient, requestTimeoutMillis)
    private val oauth = TrackerHttpClient(oauthUrl, httpClient, requestTimeoutMillis)

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val suppliedToken = credentials["token"]?.trim().orEmpty()
        val username = credentials["username"]?.trim().orEmpty()
        val password = credentials["password"].orEmpty()
        if (suppliedToken.isBlank() && (username.isBlank() || password.isBlank())) return false

        return try {
            val accessToken = suppliedToken.ifBlank { requestPasswordToken(username, password) }
            val account = graphQl(CURRENT_ACCOUNT_QUERY, buildJsonObject { }, accessToken)
                .dataObject("currentAccount")
                ?: throw TrackerApiException("Kitsu did not return the current account")
            val displayName = account.objectValue("profile")?.stringValue("name")
            setLoggedIn(true, displayName, accessToken)
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
        setLoggedIn(true, info.username.takeIf { it.isNotBlank() }, info.token)
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val response = graphQl(SEARCH_QUERY, buildJsonObject { put("query", query) }, token)
        return response.dataObject("searchMangaByTitle")
            ?.arrayValue("nodes")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull(::toSearchResult)
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        requireLoggedIn()
        val manga = graphQl(FIND_QUERY, buildJsonObject { put("remoteId", track.remoteId) }, token)
            .dataObject("findMangaById")
            ?: return null
        val entry = manga.objectValue("myLibraryEntry") ?: return null
        return track.copy(
            libraryId = entry.stringValue("id")?.toLongOrNull() ?: track.libraryId,
            title = manga.objectValue("titles")?.stringValue("preferred") ?: track.title,
            totalChapters = manga.longValue("chapterCount") ?: track.totalChapters,
            lastChapterRead = entry.longValue("progress")?.toDouble() ?: track.lastChapterRead,
            score = entry.longValue("rating")?.toDouble() ?: track.score,
            status = entry.stringValue("status")?.toTrackStatus()?.value ?: track.status,
            startedReadingDate = entry.stringValue("startedAt").toEpochMillisOrZero(),
            finishedReadingDate = entry.stringValue("finishedAt").toEpochMillisOrZero(),
            private = entry.booleanValue("private") ?: track.private,
            trackingUrl = manga.stringValue("slug")?.let { "$KITSU_MANGA_URL$it" } ?: track.trackingUrl,
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        requireLoggedIn()
        val variables = buildJsonObject {
            put("status", TrackStatus.fromValue(track.status).toKitsuStatus())
            put("progress", track.lastChapterRead.toInt())
            put("private", track.private)
            if (track.score > 0.0) put("rating", track.score.toInt())
            if (track.libraryId > 0L) put("libraryId", track.libraryId) else put("mediaId", track.remoteId)
        }
        val payload = graphQl(
            if (track.libraryId > 0L) UPDATE_QUERY else CREATE_QUERY,
            variables,
            token,
        )
        val operation = if (track.libraryId > 0L) "update" else "create"
        val entry = payload.dataObject("libraryEntry")
            ?.objectValue(operation)
            ?.objectValue("libraryEntry")
            ?: throw TrackerApiException("Kitsu did not return a library entry")
        return track.copy(libraryId = entry.stringValue("id")?.toLongOrNull() ?: track.libraryId)
    }

    private suspend fun requestPasswordToken(username: String, password: String): String {
        val request = Request.Builder()
            .url(oauth.baseUrl)
            .post(
                FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .add("grant_type", "password")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .build(),
            )
            .build()
        return oauth.execute(request).let { body ->
            defaultTrackerJson.parseToJsonElement(body).jsonObject.stringValue("access_token")
                ?: throw TrackerApiException("Kitsu did not return an access token")
        }
    }

    private suspend fun graphQl(query: String, variables: JsonObject, authToken: String?): JsonObject {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val request = Request.Builder()
            .url(graphql.baseUrl)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${authToken.orEmpty()}")
            .post(payload.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE))
            .build()
        return defaultTrackerJson.parseToJsonElement(graphql.execute(request)).jsonObject
    }

    private fun requireLoggedIn() {
        if (!isLoggedIn || token.isNullOrBlank()) throw TrackerApiException("Not logged in to Kitsu")
    }

    private fun toSearchResult(manga: JsonObject): TrackSearchResult? {
        val remoteId = manga.stringValue("id")?.toLongOrNull() ?: return null
        val title = manga.objectValue("titles")?.stringValue("preferred").orEmpty()
        if (title.isBlank()) return null
        return TrackSearchResult(
            trackerId = id,
            remoteId = remoteId,
            title = title,
            totalChapters = manga.longValue("chapterCount") ?: 0L,
            coverUrl = manga.objectValue("posterImage")?.objectValue("original")?.stringValue("url").orEmpty(),
            trackingUrl = manga.stringValue("slug")?.let { "$KITSU_MANGA_URL$it" }.orEmpty(),
            summary = manga.objectValue("description")?.stringValue("en").orEmpty(),
        )
    }

    private companion object {
        private const val CLIENT_ID =
            "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET =
            "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"
        private const val CURRENT_ACCOUNT_QUERY = "query CurrentAccount { currentAccount { id profile { name } } }"
        private const val SEARCH_QUERY =
            "query Search(\$query: String!) { searchMangaByTitle(title: \$query, first: 20) { " +
                "nodes { id titles { preferred } chapterCount posterImage { original { url } } " +
                "description(locales: \"en\") slug } } }"
        private const val FIND_QUERY =
            "query Find(\$remoteId: ID!) { findMangaById(id: \$remoteId) { id titles { preferred } " +
                "chapterCount slug myLibraryEntry { id private progress rating status startedAt finishedAt } } }"
        private const val CREATE_QUERY =
            "mutation Create(\$mediaId: ID!, \$status: LibraryEntryStatusEnum!, \$progress: Int!, " +
                "\$private: Boolean!, \$rating: Int) { libraryEntry { create(input: { mediaId: \$mediaId " +
                "mediaType: MANGA status: \$status progress: \$progress private: \$private rating: \$rating }) " +
                "{ libraryEntry { id } } } }"
        private const val UPDATE_QUERY =
            "mutation Update(\$libraryId: ID!, \$status: LibraryEntryStatusEnum!, \$progress: Int!, " +
                "\$private: Boolean!, \$rating: Int) { libraryEntry { update(input: { id: \$libraryId " +
                "status: \$status progress: \$progress private: \$private rating: \$rating }) { libraryEntry { id } } } }"
    }
}

private fun JsonObject.dataObject(name: String): JsonObject? = objectValue("data")?.objectValue(name)
private fun JsonObject.objectValue(name: String): JsonObject? = this[name]?.jsonObject
private fun JsonObject.arrayValue(name: String): JsonArray? = this[name]?.jsonArray
private fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.content
private fun JsonObject.longValue(name: String): Long? = stringValue(name)?.toLongOrNull()
private fun JsonObject.booleanValue(name: String): Boolean? = stringValue(name)?.toBooleanStrictOrNull()
private fun String?.toEpochMillisOrZero(): Long =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) } ?: 0L
private fun String.toTrackStatus(): TrackStatus? = when (this) {
    "CURRENT" -> TrackStatus.READING
    "COMPLETED" -> TrackStatus.COMPLETED
    "ON_HOLD" -> TrackStatus.ON_HOLD
    "DROPPED" -> TrackStatus.DROPPED
    "PLANNED" -> TrackStatus.PLAN_TO_READ
    else -> null
}
private fun TrackStatus.toKitsuStatus(): String = when (this) {
    TrackStatus.READING, TrackStatus.REREADING -> "CURRENT"
    TrackStatus.COMPLETED -> "COMPLETED"
    TrackStatus.ON_HOLD -> "ON_HOLD"
    TrackStatus.DROPPED -> "DROPPED"
    TrackStatus.PLAN_TO_READ -> "PLANNED"
}
