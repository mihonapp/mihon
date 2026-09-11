package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DEFAULT_MAL_BASE_URL = "https://api.myanimelist.net/v2"
private const val MAL_MANGA_URL = "https://myanimelist.net/manga/"
private const val MAX_MAL_QUERY_LENGTH = 64
private const val DEFAULT_MAL_SEARCH_LIMIT = 50
private const val MAL_SEARCH_FIELDS =
    "id,title,synopsis,num_chapters,mean,main_picture,media_type"
private const val MAL_LIST_STATUS_FIELDS =
    "status,score,num_chapters_read,start_date,finish_date,is_rereading"

/**
 * MyAnimeList API v2 tracker.
 *
 * The desktop UI collects an OAuth access token through the password/token field. Login validates
 * it with `GET /users/@me`, and list operations use the documented v2 manga endpoints.
 */
class MyAnimeListTracker(
    id: Long = 1L,
    private val baseUrl: String = DEFAULT_MAL_BASE_URL,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
    private val json: Json = defaultTrackerJson,
    private val searchLimit: Int = DEFAULT_MAL_SEARCH_LIMIT,
) : BaseDesktopTracker(
    id = id,
    name = "MyAnimeList",
    authType = TrackerAuthType.CREDENTIALS,
) {
    private val http = TrackerHttpClient(baseUrl, httpClient, requestTimeoutMillis)

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.token.isBlank()) return
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
            val user = fetchCurrentUser(authToken)
            setLoggedIn(true, user = user.name?.takeIf { it.isNotBlank() }, authToken = authToken)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            setLoggedIn(false)
            false
        }
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            // MAL API throws a 400 when the query is over 64 characters.
            .addQueryParameter("q", query.take(MAX_MAL_QUERY_LENGTH))
            .addQueryParameter("limit", searchLimit.toString())
            .addQueryParameter("nsfw", "true")
            .addQueryParameter("fields", MAL_SEARCH_FIELDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .authorized()
            .build()

        val response = decode(http.execute(request), MalSearchResponse.serializer())
        return response.data.orEmpty()
            .mapNotNull { it.node }
            .filterNot { it.mediaType?.contains("novel", ignoreCase = true) == true }
            .map { it.toSearchResult() }
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        requireLoggedIn()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(track.remoteId.toString())
            .addPathSegment("my_list_status")
            .addQueryParameter("fields", MAL_LIST_STATUS_FIELDS)
            .build()
        val body = try {
            http.execute(
                Request.Builder()
                    .url(url)
                    .get()
                    .authorized()
                    .build(),
            )
        } catch (error: TrackerHttpException) {
            if (error.code == 404) return null
            throw error
        }

        val status = decode(body, MalListStatus.serializer())
        val startedReadingDate = parseMalDate(status.startDate)
        val finishedReadingDate = parseMalDate(status.finishDate)
        return track.copy(
            lastChapterRead = status.numChaptersRead ?: 0.0,
            score = (status.score ?: 0).toDouble(),
            status = status.toTrackStatus(track.status),
            startedReadingDate = startedReadingDate.takeIf { it > 0L } ?: track.startedReadingDate,
            finishedReadingDate = finishedReadingDate.takeIf { it > 0L } ?: track.finishedReadingDate,
            trackingUrl = "$MAL_MANGA_URL${track.remoteId}",
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        requireLoggedIn()
        val formBuilder = FormBody.Builder()
            .add("status", track.status.toMalStatus())
            .add("is_rereading", (track.status == TrackStatus.REREADING.value).toString())
            .add("score", track.score.coerceIn(0.0, 10.0).toInt().toString())
            .add("num_chapters_read", track.lastChapterRead.toInt().toString())
        formatMalDate(track.startedReadingDate)?.let { formBuilder.add("start_date", it) }
        formatMalDate(track.finishedReadingDate)?.let { formBuilder.add("finish_date", it) }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(track.remoteId.toString())
            .addPathSegment("my_list_status")
            .build()
        val request = Request.Builder()
            .url(url)
            .patch(formBuilder.build())
            .authorized()
            .build()

        val status = decode(http.execute(request), MalListStatus.serializer())
        val startedReadingDate = parseMalDate(status.startDate)
        val finishedReadingDate = parseMalDate(status.finishDate)
        return track.copy(
            lastChapterRead = status.numChaptersRead ?: 0.0,
            score = (status.score ?: 0).toDouble(),
            status = status.toTrackStatus(track.status),
            startedReadingDate = startedReadingDate.takeIf { it > 0L } ?: track.startedReadingDate,
            finishedReadingDate = finishedReadingDate.takeIf { it > 0L } ?: track.finishedReadingDate,
        )
    }

    private fun Request.Builder.authorized(): Request.Builder = apply {
        token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
    }

    private fun requireLoggedIn() {
        if (!isLoggedIn || token.isNullOrBlank()) {
            throw TrackerApiException("Not logged in to MyAnimeList")
        }
    }

    private suspend fun fetchCurrentUser(authToken: String): MalUser {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("users")
            .addPathSegment("@me")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $authToken")
            .header("Accept", "application/json")
            .build()
        return decode(http.execute(request), MalUser.serializer())
    }

    private fun <T> decode(body: String, deserializer: DeserializationStrategy<T>): T = try {
        json.decodeFromString(deserializer, body)
    } catch (error: Exception) {
        throw TrackerApiException("Failed to parse MyAnimeList response", error)
    }

    private fun MalManga.toSearchResult(): TrackSearchResult = TrackSearchResult(
        trackerId = this@MyAnimeListTracker.id,
        remoteId = id,
        title = title.orEmpty(),
        totalChapters = numChapters ?: 0L,
        coverUrl = mainPicture?.large?.takeIf { it.isNotBlank() } ?: mainPicture?.medium.orEmpty(),
        trackingUrl = "$MAL_MANGA_URL$id",
        summary = synopsis.orEmpty(),
    )

    private fun Long.toMalStatus(): String = when (TrackStatus.fromValue(this)) {
        TrackStatus.READING, TrackStatus.REREADING -> "reading"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.ON_HOLD -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_READ -> "plan_to_read"
    }

    private fun MalListStatus.toTrackStatus(fallback: Long): Long {
        if (isRereading == true) return TrackStatus.REREADING.value
        return when (status?.lowercase()) {
            "reading" -> TrackStatus.READING.value
            "completed" -> TrackStatus.COMPLETED.value
            "on_hold" -> TrackStatus.ON_HOLD.value
            "dropped" -> TrackStatus.DROPPED.value
            "plan_to_read" -> TrackStatus.PLAN_TO_READ.value
            else -> fallback
        }
    }

    private fun parseMalDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val date = try {
            when (value.length) {
                10 -> LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                7 -> YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyy-MM")).atDay(1)
                4 -> Year.parse(value, DateTimeFormatter.ofPattern("yyyy")).atDay(1)
                else -> return 0L
            }
        } catch (_: Exception) {
            return 0L
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun formatMalDate(epochMillis: Long): String? {
        if (epochMillis <= 0L) return null
        return try {
            Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
private data class MalUser(
    val id: Long = 0L,
    val name: String? = null,
)

@Serializable
private data class MalSearchResponse(
    val data: List<MalSearchNode>? = null,
    val paging: MalPaging? = null,
)

@Serializable
private data class MalSearchNode(
    val node: MalManga? = null,
)

@Serializable
private data class MalPaging(
    val next: String? = null,
)

@Serializable
private data class MalManga(
    val id: Long = 0L,
    val title: String? = null,
    val synopsis: String? = null,
    @SerialName("num_chapters") val numChapters: Long? = null,
    val mean: Double? = null,
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
    @SerialName("media_type") val mediaType: String? = null,
)

@Serializable
private data class MalPicture(
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
private data class MalListStatus(
    val status: String? = null,
    val score: Int? = null,
    @SerialName("num_chapters_read") val numChaptersRead: Double? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("finish_date") val finishDate: String? = null,
    @SerialName("is_rereading") val isRereading: Boolean? = null,
)
