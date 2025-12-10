package eu.kanade.tachiyomi.data.track.novellist

import android.graphics.Color
import android.util.Base64
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

/**
 * NovelList tracker implementation.
 * API docs based on lnreader's implementation.
 * Website: https://www.novellist.co
 * API Base: https://novellist-be-960019704910.asia-east1.run.app
 */
class NovelList(id: Long) : BaseTracker(id, "NovelList") {

    private val json: Json by injectLazy()
    private val baseUrl = "https://novellist-be-960019704910.asia-east1.run.app"

    override fun getLogo() = R.drawable.ic_tracker_novellist

    override fun getLogoColor(): Int = Color.parseColor("#3399FF")

    override fun getStatusList() = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)

    override fun getStatus(status: Long): StringResource? {
        return when (status) {
            READING -> MR.strings.reading
            COMPLETED -> MR.strings.completed
            ON_HOLD -> MR.strings.on_hold
            DROPPED -> MR.strings.dropped
            PLAN_TO_READ -> MR.strings.plan_to_read
            else -> null
        }
    }

    override fun getReadingStatus() = READING
    override fun getRereadingStatus() = READING
    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf(
        "10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "",
    )

    override fun indexToScore(index: Int): Double {
        return if (index == 10) 0.0 else (10 - index).toDouble()
    }

    override fun get10PointScore(track: DomainTrack): Double {
        return track.score
    }

    override fun displayScore(track: DomainTrack): String {
        return if (track.score == 0.0) "-" else track.score.toInt().toString()
    }

    private fun mapStatusToApi(status: Long): String {
        return when (status) {
            READING -> "IN_PROGRESS"
            COMPLETED -> "COMPLETED"
            ON_HOLD -> "PLANNED"
            DROPPED -> "DROPPED"
            PLAN_TO_READ -> "PLANNED"
            else -> "IN_PROGRESS"
        }
    }

    private fun mapStatusFromApi(status: String): Long {
        return when (status) {
            "IN_PROGRESS" -> READING
            "COMPLETED" -> COMPLETED
            "PLANNED" -> PLAN_TO_READ
            "DROPPED" -> DROPPED
            else -> READING
        }
    }

    /**
     * Extract UUID from tracking_url
     * Format: https://www.novellist.co/novel/slug#uuid
     */
    private fun getUuidFromTrack(track: Track): String {
        return track.tracking_url.substringAfter("#", "")
    }

    /**
     * Build authenticated request with proper headers following TypeScript implementation
     */
    private fun buildAuthenticatedRequest(url: String): Request.Builder {
        val token = getPassword()
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "en-US,en;q=0.5")
            .addHeader("Origin", "https://www.novellist.co")
            .addHeader("Referer", "https://www.novellist.co/")
            .addHeader("Sec-Fetch-Dest", "empty")
            .addHeader("Sec-Fetch-Mode", "cors")
            .addHeader("Sec-Fetch-Site", "cross-site")
    }

    /**
     * Send OPTIONS preflight request before PUT/POST requests
     */
    private suspend fun sendOptionsRequest(url: String, method: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .method("OPTIONS", null)
                .addHeader("Accept", "*/*")
                .addHeader("Access-Control-Request-Method", method)
                .addHeader("Access-Control-Request-Headers", "authorization,content-type")
                .addHeader("Origin", "https://www.novellist.co")
                .addHeader("Referer", "https://www.novellist.co/")
                .addHeader("Sec-Fetch-Dest", "empty")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Site", "cross-site")
                .build()
            client.newCall(request).awaitSuccess()
        } catch (e: Exception) {
            // OPTIONS preflight may fail, continue anyway
            logcat(LogPriority.DEBUG) { "OPTIONS preflight failed: ${e.message}" }
        }
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        val uuid = getUuidFromTrack(track)
        if (uuid.isEmpty()) return track

        val url = "$baseUrl/api/users/current/reading-list/$uuid"

        // Send OPTIONS preflight first
        sendOptionsRequest(url, "PUT")

        val requestBody = buildJsonObject {
            put("chapter_count", track.last_chapter_read.toInt())
            put("status", mapStatusToApi(track.status))
            if (track.score > 0) {
                put("rating", track.score.toInt())
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val request = buildAuthenticatedRequest(url)
            .put(requestBody)
            .build()

        client.newCall(request).awaitSuccess()
        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val uuid = getUuidFromTrack(track)
        if (uuid.isEmpty()) return track

        val url = "$baseUrl/api/users/current/reading-list/$uuid"

        // Send OPTIONS preflight first
        sendOptionsRequest(url, "PUT")

        // NovelList uses PUT to the same endpoint for both adding and updating
        val requestBody = buildJsonObject {
            put("status", if (hasReadChapters) "IN_PROGRESS" else "PLANNED")
            put("chapter_count", track.last_chapter_read.toInt())
            put("rating", 0)
            put("note", "")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = buildAuthenticatedRequest(url)
            .put(requestBody)
            .build()

        client.newCall(request).awaitSuccess()

        track.status = if (hasReadChapters) READING else PLAN_TO_READ
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        val requestBody = buildJsonObject {
            put("page", 1)
            put("sort_order", "MOST_TRENDING")
            put("title_search_query", query)
            put("language", "UNKNOWN")
            putJsonArray("label_ids") {}
            putJsonArray("excluded_label_ids") {}
        }.toString().toRequestBody("application/json".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("$baseUrl/api/novels/filter")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Origin", "https://www.novellist.co")
            .addHeader("Referer", "https://www.novellist.co/")
            .build()

        return try {
            val response = client.newCall(request).awaitSuccess()
            val responseBody = response.body.string()

            val jsonArray = json.decodeFromString<List<JsonObject>>(responseBody)
            jsonArray.map { obj ->
                val track = TrackSearch.create(id)
                // ID is a UUID string - store it for API calls
                val idString = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                // Use hash for remote_id but keep UUID in tracking_url for API
                track.remote_id = idString.hashCode().toLong().let { if (it < 0) -it else it }
                track.title = obj["english_title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["raw_title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                track.cover_url = obj["cover_image_link"]?.jsonPrimitive?.contentOrNull
                    ?: obj["image_url"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                track.summary = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: idString
                // Store UUID in tracking_url - use special format to preserve both slug and id
                // Format: https://www.novellist.co/novel/slug#uuid
                track.tracking_url = "https://www.novellist.co/novel/$slug#$idString"
                track.publishing_status = obj["status"]?.jsonPrimitive?.contentOrNull ?: ""
                track
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "NovelList search failed" }
            emptyList()
        }
    }

    override suspend fun refresh(track: Track): Track {
        val uuid = getUuidFromTrack(track)
        if (uuid.isEmpty()) return track

        val url = "$baseUrl/api/users/current/reading-list/$uuid"
        val request = buildAuthenticatedRequest(url)
            .get()
            .build()

        return try {
            val response = client.newCall(request).awaitSuccess()
            with(json) {
                val obj = response.parseAs<JsonObject>()
                track.status = mapStatusFromApi(obj["status"]?.jsonPrimitive?.contentOrNull ?: "IN_PROGRESS")
                track.last_chapter_read = (obj["chapter_count"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0)
                track.score = (obj["rating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0)
            }
            track
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "NovelList refresh failed" }
            track
        }
    }

    /**
     * Extract JWT token from novellist cookie.
     * Cookie format: base64-{base64 encoded JSON containing access_token}
     */
    fun extractTokenFromCookie(cookieValue: String): String? {
        return try {
            if (cookieValue.startsWith("base64-")) {
                val base64Part = cookieValue.removePrefix("base64-")
                val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
                val decodedString = String(decodedBytes, Charsets.UTF_8)
                val jsonObj = json.decodeFromString<JsonObject>(decodedString)
                jsonObj["access_token"]?.jsonPrimitive?.contentOrNull
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to extract token from cookie" }
            null
        }
    }

    override suspend fun login(username: String, password: String) {
        // For NovelList:
        // - username: display name (optional)
        // - password: JWT access token (required)
        // Token should be extracted from the 'novellist' cookie after logging in via WebView
        if (password.isBlank()) {
            throw Exception("Access token is required. Please login via WebView first.")
        }
        saveCredentials(username.ifBlank { "NovelList User" }, password)
    }

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L

        const val LOGIN_URL = "https://www.novellist.co/login"
    }
}
