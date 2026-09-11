package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DEFAULT_ANILIST_BASE_URL = "https://graphql.anilist.co"
private const val ANILIST_MANGA_URL = "https://anilist.co/manga/"
private const val ANILIST_AUTH_URL =
    "https://anilist.co/api/v2/oauth/authorize?client_id=3865&response_type=token"

/**
 * AniList GraphQL tracker.
 *
 * Login exchanges a personal/OAuth token for the viewer profile (id + name). Search uses the
 * public `Page.media` query, while find/update use the authenticated `MediaList` query and
 * `SaveMediaListEntry` mutation respectively.
 */
class AniListTracker(
    id: Long = 2L,
    private val baseUrl: String = DEFAULT_ANILIST_BASE_URL,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
    private val json: Json = defaultTrackerJson,
) : BaseDesktopTracker(
    id = id,
    name = "AniList",
    authType = TrackerAuthType.TOKEN,
    authUrl = ANILIST_AUTH_URL,
) {
    private val http = TrackerHttpClient(baseUrl, httpClient, requestTimeoutMillis)
    private var userId: Long? = null

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.token.isBlank()) return
        userId = null
        setLoggedIn(
            value = true,
            user = info.username.takeIf { it.isNotBlank() },
            authToken = info.token,
            url = info.serverUrl.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val authToken = (credentials["token"] ?: credentials["password"] ?: credentials["access_token"])
            ?.trim()
            .orEmpty()
        if (authToken.isBlank()) return false

        return try {
            val viewer = fetchViewer(authToken)
            userId = viewer.id
            setLoggedIn(true, user = viewer.name?.takeIf { it.isNotBlank() }, authToken = authToken)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            setLoggedIn(false)
            false
        }
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val data = graphql(
            query = SEARCH_QUERY,
            variables = buildJsonObject { put("query", query) },
            authToken = token,
            deserializer = AniListSearchData.serializer(),
        )

        return data.page?.media.orEmpty().map { media ->
            TrackSearchResult(
                trackerId = id,
                remoteId = media.id,
                title = media.title?.userPreferred.orEmpty(),
                totalChapters = media.chapters ?: 0L,
                coverUrl = media.coverImage?.large.orEmpty(),
                trackingUrl = "$ANILIST_MANGA_URL${media.id}",
                summary = media.description.orEmpty(),
            )
        }
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        val viewerId = ensureViewerId()
        val data = graphql(
            query = FIND_MEDIA_LIST_QUERY,
            variables = buildJsonObject {
                put("userId", viewerId)
                put("mediaId", track.remoteId)
            },
            authToken = token,
            deserializer = AniListMediaListData.serializer(),
        )

        val entry = data.mediaList ?: return null
        val media = entry.media
        val remoteMediaId = media?.id ?: track.remoteId
        return track.copy(
            remoteId = remoteMediaId,
            libraryId = entry.id,
            title = media?.title?.userPreferred?.takeIf { it.isNotBlank() } ?: track.title,
            totalChapters = media?.chapters ?: track.totalChapters,
            lastChapterRead = entry.progress.toDouble(),
            score = entry.scoreRaw ?: track.score,
            status = anilistStatusToTrackStatus(entry.status) ?: track.status,
            startedReadingDate = entry.startedAt.toEpochMillis().takeIf { it > 0 } ?: track.startedReadingDate,
            finishedReadingDate = entry.completedAt.toEpochMillis().takeIf { it > 0 } ?: track.finishedReadingDate,
            private = entry.isPrivate,
            trackingUrl = "$ANILIST_MANGA_URL$remoteMediaId",
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        requireLoggedIn()
        val variables = buildJsonObject {
            if (track.libraryId > 0L) {
                put("id", track.libraryId)
            } else {
                put("mediaId", track.remoteId)
            }
            put("status", track.status.toAniListStatus())
            put("score", track.score.toInt())
            put("progress", track.lastChapterRead.toInt())
            put("private", track.private)
            val startedAt = track.startedReadingDate.toAniListDate()
            val completedAt = track.finishedReadingDate.toAniListDate()
            put("startedAt", startedAt ?: JsonNull)
            put("completedAt", completedAt ?: JsonNull)
        }

        val mutation = if (track.libraryId > 0L) UPDATE_MUTATION else ADD_MUTATION
        val data = graphql(
            query = mutation,
            variables = variables,
            authToken = token,
            deserializer = AniListSaveData.serializer(),
        )
        val entry = data.entry ?: throw TrackerApiException("AniList did not return a media list entry")

        return track.copy(
            libraryId = entry.id,
            lastChapterRead = entry.progress.toDouble(),
            status = anilistStatusToTrackStatus(entry.status) ?: track.status,
        )
    }

    private fun requireLoggedIn() {
        if (!isLoggedIn || token.isNullOrBlank()) {
            throw TrackerApiException("Not logged in to AniList")
        }
    }

    private suspend fun ensureViewerId(): Long {
        val cached = userId
        if (cached != null && cached > 0L) return cached

        requireLoggedIn()
        val authToken = token ?: throw TrackerApiException("Not logged in to AniList")
        val viewer = fetchViewer(authToken)
        userId = viewer.id
        setLoggedIn(true, user = viewer.name?.takeIf { it.isNotBlank() }, authToken = authToken, url = serverUrl)
        return viewer.id
    }

    private suspend fun fetchViewer(authToken: String): AniListViewer =
        graphql(
            query = VIEWER_QUERY,
            variables = buildJsonObject { },
            authToken = authToken,
            deserializer = AniListViewerData.serializer(),
        ).viewer ?: throw TrackerApiException("AniList did not return a viewer profile")

    private suspend fun <T> graphql(
        query: String,
        variables: JsonObject,
        authToken: String?,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val requestBuilder = Request.Builder()
            .url(http.baseUrl)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(TRACKER_JSON_MEDIA_TYPE))
        if (!authToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $authToken")
        }

        val bodyText = http.execute(requestBuilder.build())
        val root = try {
            json.parseToJsonElement(bodyText).jsonObject
        } catch (error: Exception) {
            throw TrackerApiException("AniList returned an invalid response", error)
        }

        val errors = root["errors"]
        if (errors is JsonArray && errors.isNotEmpty()) {
            throw TrackerApiException("AniList API error: ${errors.joinToString { it.toString() }}")
        }
        val data = root["data"] as? JsonObject
            ?: throw TrackerApiException("AniList response did not contain data")

        return try {
            json.decodeFromJsonElement(deserializer, data)
        } catch (error: Exception) {
            throw TrackerApiException("Failed to parse AniList response", error)
        }
    }

    private fun Long.toAniListStatus(): String = when (TrackStatus.fromValue(this)) {
        TrackStatus.READING -> "CURRENT"
        TrackStatus.COMPLETED -> "COMPLETED"
        TrackStatus.ON_HOLD -> "PAUSED"
        TrackStatus.DROPPED -> "DROPPED"
        TrackStatus.PLAN_TO_READ -> "PLANNING"
        TrackStatus.REREADING -> "REPEATING"
    }

    private fun anilistStatusToTrackStatus(status: String?): Long? = when (status) {
        "CURRENT" -> TrackStatus.READING.value
        "COMPLETED" -> TrackStatus.COMPLETED.value
        "PAUSED" -> TrackStatus.ON_HOLD.value
        "DROPPED" -> TrackStatus.DROPPED.value
        "PLANNING" -> TrackStatus.PLAN_TO_READ.value
        "REPEATING" -> TrackStatus.REREADING.value
        else -> null
    }

    private fun Long.toAniListDate(): JsonObject? {
        if (this <= 0L) return null
        val date = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
        return buildJsonObject {
            put("year", date.year)
            put("month", date.monthValue)
            put("day", date.dayOfMonth)
        }
    }

    private fun AniListFuzzyDate?.toEpochMillis(): Long {
        val year = this?.year ?: return 0L
        val month = this.month ?: return 0L
        val day = this.day ?: return 0L
        return try {
            LocalDate.of(year, month, day)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private companion object {
        private const val VIEWER_QUERY = """
            query Viewer {
              Viewer {
                id
                name
              }
            }
        """

        private const val SEARCH_QUERY = """
            query Search(${"$"}query: String) {
              Page(perPage: 50) {
                media(type: MANGA, search: ${"$"}query, format_not_in: [NOVEL]) {
                  id
                  title {
                    userPreferred
                  }
                  coverImage {
                    large
                  }
                  chapters
                  description
                }
              }
            }
        """

        private const val FIND_MEDIA_LIST_QUERY = """
            query FindMediaList(${"$"}userId: Int!, ${"$"}mediaId: Int!) {
              MediaList(userId: ${"$"}userId, mediaId: ${"$"}mediaId) {
                id
                status
                scoreRaw: score(format: POINT_100)
                progress
                private
                startedAt {
                  year
                  month
                  day
                }
                completedAt {
                  year
                  month
                  day
                }
                media {
                  id
                  title {
                    userPreferred
                  }
                  coverImage {
                    large
                  }
                  chapters
                  description
                }
              }
            }
        """

        private const val ADD_MUTATION = """
            mutation AddMediaListEntry(
              ${"$"}mediaId: Int!,
              ${"$"}status: MediaListStatus,
              ${"$"}score: Int,
              ${"$"}progress: Int,
              ${"$"}private: Boolean,
              ${"$"}startedAt: FuzzyDateInput,
              ${"$"}completedAt: FuzzyDateInput
            ) {
              SaveMediaListEntry(
                mediaId: ${"$"}mediaId,
                status: ${"$"}status,
                scoreRaw: ${"$"}score,
                progress: ${"$"}progress,
                private: ${"$"}private,
                startedAt: ${"$"}startedAt,
                completedAt: ${"$"}completedAt
              ) {
                id
                status
                progress
              }
            }
        """

        private const val UPDATE_MUTATION = """
            mutation UpdateMediaListEntry(
              ${"$"}id: Int!,
              ${"$"}status: MediaListStatus,
              ${"$"}score: Int,
              ${"$"}progress: Int,
              ${"$"}private: Boolean,
              ${"$"}startedAt: FuzzyDateInput,
              ${"$"}completedAt: FuzzyDateInput
            ) {
              SaveMediaListEntry(
                id: ${"$"}id,
                status: ${"$"}status,
                scoreRaw: ${"$"}score,
                progress: ${"$"}progress,
                private: ${"$"}private,
                startedAt: ${"$"}startedAt,
                completedAt: ${"$"}completedAt
              ) {
                id
                status
                progress
              }
            }
        """
    }
}

@Serializable
private data class AniListViewerData(
    @SerialName("Viewer") val viewer: AniListViewer? = null,
)

@Serializable
private data class AniListViewer(
    val id: Long = 0L,
    val name: String? = null,
)

@Serializable
private data class AniListSearchData(
    @SerialName("Page") val page: AniListSearchPage? = null,
)

@Serializable
private data class AniListSearchPage(
    val media: List<AniListMedia>? = null,
)

@Serializable
private data class AniListMedia(
    val id: Long = 0L,
    val title: AniListTitle? = null,
    val coverImage: AniListCoverImage? = null,
    val chapters: Long? = null,
    val description: String? = null,
)

@Serializable
private data class AniListTitle(
    val userPreferred: String? = null,
)

@Serializable
private data class AniListCoverImage(
    val large: String? = null,
)

@Serializable
private data class AniListMediaListData(
    @SerialName("MediaList") val mediaList: AniListMediaListEntry? = null,
)

@Serializable
private data class AniListMediaListEntry(
    val id: Long = 0L,
    val status: String? = null,
    val scoreRaw: Double? = null,
    val progress: Int = 0,
    @SerialName("private") val isPrivate: Boolean = false,
    val startedAt: AniListFuzzyDate? = null,
    val completedAt: AniListFuzzyDate? = null,
    val media: AniListMedia? = null,
)

@Serializable
private data class AniListFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
private data class AniListSaveData(
    @SerialName("SaveMediaListEntry") val entry: AniListSaveEntry? = null,
)

@Serializable
private data class AniListSaveEntry(
    val id: Long = 0L,
    val status: String? = null,
    val progress: Int = 0,
)
