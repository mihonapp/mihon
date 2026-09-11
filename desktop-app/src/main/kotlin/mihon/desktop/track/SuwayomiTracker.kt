@file:Suppress("ktlint:standard:max-line-length")

package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SuwayomiTracker(
    id: Long = 9L,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    private val requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(id, "Suwayomi", TrackerAuthType.SERVER) {
    private val client = httpClient
    private var baseUrl: String? = null
    private var secret: String = ""
    override val persistenceToken get() = secret
    override val supportedStatuses = listOf(TrackStatus.PLAN_TO_READ, TrackStatus.READING, TrackStatus.COMPLETED)
    override val supportsScore = false

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val url = (credentials["server_url"] ?: credentials["url"]).orEmpty().trim().trimEnd('/')
        if (url.isBlank()) return false
        val user = credentials["username"]?.trim().orEmpty()
        val credential = credentials["password"] ?: credentials["token"].orEmpty()
        return try {
            baseUrl = url
            secret = credential
            graphQl("query MihonDesktopProbe { __typename }", buildJsonObject { }, user, credential)
            setLoggedIn(true, user.takeIf(String::isNotBlank), credential, url)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            baseUrl = null
            secret = ""
            setLoggedIn(false)
            false
        }
    }

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.serverUrl.isBlank()) return
        baseUrl = info.serverUrl.trimEnd('/')
        secret = info.token
        setLoggedIn(true, info.username.takeIf(String::isNotBlank), info.token, baseUrl)
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val numeric = query.trim().substringAfterLast('/').toLongOrNull()
        if (numeric != null) return listOfNotNull(getManga(numeric)?.toSearch())
        val data = graphQl(SEARCH_QUERY, buildJsonObject { put("query", query.trim()) })
        return data["mangas"]?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.toSearch() }
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = getManga(
        track.remoteId,
    )?.let { manga ->
        track.copy(
            title = manga.sString("title") ?: track.title,
            totalChapters = manga.sObj("chapters")?.sLong("totalCount") ?: track.totalChapters,
            lastChapterRead = manga.sObj("latestReadChapter")?.sDouble("chapterNumber") ?: 0.0,
            status = when (manga.sLong("unreadCount")) {
                0L -> TrackStatus.COMPLETED.value
                manga.sObj("chapters")?.sLong("totalCount") -> TrackStatus.PLAN_TO_READ.value
                else -> TrackStatus.READING.value
            },
            trackingUrl = "${requireBase()}/manga/${track.remoteId}",
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        val chapters = graphQl(UNREAD_QUERY, buildJsonObject { put("mangaId", track.remoteId) })
            .sObj("chapters")?.get("nodes")?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { (it.sDouble("chapterNumber") ?: Double.MAX_VALUE) <= track.lastChapterRead + 0.001 }
            .mapNotNull { it.sLong("id") }
        graphQl(
            MARK_QUERY,
            buildJsonObject {
                put(
                    "chapters",
                    buildJsonArray {
                        chapters.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            },
        )
        graphQl(TRACK_QUERY, buildJsonObject { put("mangaId", track.remoteId) })
        return findRemote(track) ?: track
    }

    private suspend fun getManga(id: Long): JsonObject? = graphQl(
        GET_QUERY,
        buildJsonObject {
            put("mangaId", id)
        },
    ).sObj("manga")
    private suspend fun graphQl(
        query: String,
        variables: JsonObject,
        user: String = username.orEmpty(),
        credential: String = secret,
    ): JsonObject {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val builder = Request.Builder().url(
            requireBase() + "/api/graphql",
        ).post(payload.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE))
        if (user.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(user, credential))
        } else if (credential.isNotBlank()) {
            builder.header("Authorization", "Bearer $credential")
        }
        val text = TrackerHttpClient(requireBase(), client, requestTimeoutMillis).execute(builder.build())
        val root = defaultTrackerJson.parseToJsonElement(text).jsonObject
        if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) throw TrackerApiException("Suwayomi API error")
        return root.sObj("data") ?: throw TrackerApiException("Suwayomi response did not contain data")
    }
    private fun requireBase() = baseUrl ?: throw TrackerApiException("Not logged in to Suwayomi")
    private fun JsonObject.toSearch(): TrackSearchResult? {
        val mangaId = sLong("id") ?: return null
        val title = sString("title") ?: return null
        return TrackSearchResult(
            id,
            mangaId,
            title,
            sObj("chapters")?.sLong("totalCount") ?: 0L,
            "${requireBase()}/${sString("thumbnailUrl").orEmpty().trimStart('/')}",
            "${requireBase()}/manga/$mangaId",
            sString("description").orEmpty(),
        )
    }
    private companion object {
        private const val FIELDS = "id title description thumbnailUrl unreadCount chapters { totalCount } latestReadChapter { chapterNumber }"
        private const val GET_QUERY = "query GetManga(\$mangaId: Int!) { manga(id: \$mangaId) { $FIELDS } }"
        private const val SEARCH_QUERY = "query Search(\$query: String!) { mangas(condition: {title: {includesInsensitive: \$query}}) { nodes { $FIELDS } } }"
        private const val UNREAD_QUERY = "query Unread(\$mangaId: Int!) { chapters(condition: {mangaId: \$mangaId, isRead: false}) { nodes { id chapterNumber } } }"
        private const val MARK_QUERY = "mutation Mark(\$chapters: [Int!]!) { updateChapters(input: {ids: \$chapters, patch: {isRead: true}}) { __typename } }"
        private const val TRACK_QUERY = "mutation Track(\$mangaId: Int!) { trackProgress(input: {mangaId: \$mangaId}) { __typename } }"
    }
}
private fun JsonObject.sObj(name: String) = this[name] as? JsonObject
private fun JsonObject.sString(name: String) = this[name]?.jsonPrimitive?.content
private fun JsonObject.sLong(name: String) = sString(name)?.toLongOrNull()
private fun JsonObject.sDouble(name: String) = sString(name)?.toDoubleOrNull()
