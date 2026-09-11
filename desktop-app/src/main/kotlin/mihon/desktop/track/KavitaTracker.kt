@file:Suppress("ktlint:standard:max-line-length")

package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class KavitaTracker(
    id: Long = 8L,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    private val requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(id, "Kavita", TrackerAuthType.SERVER) {
    private val client = httpClient
    private var apiKey: String? = null
    private var apiUrl: String? = null
    override val persistenceToken get() = apiKey
    override val supportedStatuses = listOf(TrackStatus.PLAN_TO_READ, TrackStatus.READING, TrackStatus.COMPLETED)
    override val supportsScore = false

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val url = (credentials["server_url"] ?: credentials["url"]).orEmpty().trim().trimEnd('/').removeSuffix("/api")
        val key = (credentials["token"] ?: credentials["password"]).orEmpty().trim()
        if (url.isBlank() || key.isBlank()) return false
        return try {
            apiUrl = "$url/api"
            apiKey = key
            val authUrl = "$url/api/Plugin/authenticate".toHttpUrl().newBuilder()
                .addQueryParameter("apiKey", key).addQueryParameter("pluginName", "Tachiyomi-Kavita").build()
            val response = execute(
                Request.Builder().url(authUrl).post("{}".toRequestBody(TRACKER_JSON_MEDIA_TYPE)).build(),
                false,
            ) as? JsonObject ?: throw TrackerApiException("Kavita returned an invalid login response")
            val jwt = response.kString("token") ?: throw TrackerApiException("Kavita did not return a token")
            setLoggedIn(true, response.kString("username"), jwt, url)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            apiUrl = null
            apiKey = null
            setLoggedIn(false)
            false
        }
    }

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.serverUrl.isBlank() || info.token.isBlank()) return
        apiUrl = info.serverUrl.trimEnd('/').removeSuffix("/api") + "/api"
        apiKey = info.token
        setLoggedIn(
            true,
            info.username.takeIf(String::isNotBlank),
            info.token,
            info.serverUrl.trimEnd('/').removeSuffix("/api"),
        )
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        ensureSession()
        val root = execute(auth("/Series/all-v2?pageNumber=1&pageSize=100"))
        val values = when (root) {
            is JsonArray -> root
            is JsonObject -> root["result"] as? JsonArray
                ?: root["data"] as? JsonArray
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return values.mapNotNull {
            it as? JsonObject
        }.filter { it.kString("name").orEmpty().contains(query, true) }.mapNotNull(::toResult)
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        ensureSession()
        val series = try {
            execute(auth("/Series/${track.remoteId}")) as JsonObject
        } catch (
            e: TrackerHttpException,
        ) {
            if (e.code ==
                404
            ) {
                return null
            } else {
                throw e
            }
        }
        val latest = execute(auth("/Tachiyomi/latest-chapter?seriesId=${track.remoteId}"), allowEmpty = true)
        val chapter = (latest as? JsonObject)?.kString("number")?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        val total = (execute(auth("/Series/volumes?seriesId=${track.remoteId}")) as? JsonArray).orEmpty()
            .flatMap { (it as? JsonObject)?.get("chapters")?.jsonArray.orEmpty() }
            .mapNotNull {
                (it as? JsonObject)?.kString("number")?.replace(',', '.')?.toDoubleOrNull()
            }.maxOrNull()?.toLong()
            ?: 0L
        val pages = series.kLong("pages") ?: 0
        val read = series.kLong("pagesRead") ?: 0
        return track.copy(
            title = series.kString("name") ?: track.title,
            totalChapters = total,
            lastChapterRead = chapter,
            status = when {
                pages > 0 && read >= pages -> TrackStatus.COMPLETED.value
                read > 0 -> TrackStatus.READING.value
                else -> TrackStatus.PLAN_TO_READ.value
            },
            trackingUrl = "${requireApi()}/Series/${track.remoteId}",
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        ensureSession()
        val path = "/Tachiyomi/mark-chapter-until-as-read?seriesId=${track.remoteId}&chapterNumber=${track.lastChapterRead}"
        execute(auth(path, "POST"), allowEmpty = true)
        return findRemote(track) ?: track
    }

    private suspend fun ensureSession() {
        if (!isLoggedIn) throw TrackerApiException("Not logged in to Kavita")
        if (token == apiKey) {
            val server = serverUrl ?: throw TrackerApiException("Kavita server is missing")
            if (!login(
                    mapOf("server_url" to server, "token" to apiKey.orEmpty()),
                )
            ) {
                throw TrackerApiException("Kavita session expired")
            }
        }
    }
    private fun requireApi() = apiUrl ?: throw TrackerApiException("Kavita server is missing")
    private fun auth(path: String, method: String = "GET"): Request = Request.Builder().url(requireApi() + path)
        .header("Authorization", "Bearer ${token.orEmpty()}").method(
            method,
            if (method ==
                "GET"
            ) {
                null
            } else {
                "{}".toRequestBody(TRACKER_JSON_MEDIA_TYPE)
            },
        ).build()
    private suspend fun execute(request: Request, allowEmpty: Boolean = false): kotlinx.serialization.json.JsonElement {
        val text = TrackerHttpClient(requireApi(), client, requestTimeoutMillis).execute(request)
        return if (text.isBlank() && allowEmpty) JsonObject(emptyMap()) else defaultTrackerJson.parseToJsonElement(text)
    }
    private fun toResult(series: JsonObject): TrackSearchResult? {
        val remoteId = series.kLong("id") ?: return null
        val title = series.kString("name") ?: return null
        return TrackSearchResult(id, remoteId, title, trackingUrl = "${requireApi()}/Series/$remoteId")
    }
}
private fun JsonObject.kString(name: String) = this[name]?.jsonPrimitive?.content
private fun JsonObject.kLong(name: String) = kString(name)?.toLongOrNull()
