package mihon.desktop.track

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

class MangaBakaTracker(
    baseUrl: String = "https://api.mangabaka.org",
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
) : JsonTokenTracker(11L, "MangaBaka", baseUrl, httpClient) {
    override val profilePath = "/v1/my/profile"
    override fun account(profile: JsonObject): String? = profile.tokenObject("data").let {
        it.tokenText("preferred_username") ?: it.tokenText("nickname") ?: it.tokenText("id")
    }
    override suspend fun search(query: String): List<TrackSearchResult> =
        request("/v1/series/search?q=${URLEncoder.encode(query, Charsets.UTF_8)}&type_not=novel")
            .tokenArray("data").map { value ->
                val item = value.jsonObject
                val remoteId = item.tokenNumber("id").toLong()
                val titles = item.tokenArray("titles").map { it.jsonObject }
                val languages = listOf("en", "ja-Latn", "ja", "ko-Latn", "ko", "zh-Latn", "zh")
                val title = languages.firstNotNullOfOrNull { language ->
                    titles.filter { it.tokenText("language") == language }.minByOrNull {
                        when {
                            it.tokenText("is_primary") == "true" -> 0
                            it.tokenArray("traits").any { trait -> trait.jsonPrimitive.content == "official" } -> 1
                            it.tokenArray("traits").any { trait -> trait.jsonPrimitive.content == "native" } -> 2
                            else -> 3
                        }
                    }?.tokenText("title")
                } ?: titles.firstOrNull()?.tokenText("title") ?: "ID: $remoteId"
                TrackSearchResult(
                    id,
                    remoteId,
                    title,
                    coverUrl = item.tokenObject("cover").tokenObject("x250").tokenText("x1").orEmpty(),
                    trackingUrl = "https://mangabaka.org/$remoteId",
                    summary = item.tokenText("description").orEmpty(),
                )
            }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        val data = optional("/v1/my/library/${track.remoteId}")?.tokenObject("data") ?: return null
        return track.copy(
            lastChapterRead = data.tokenNumber("progress_chapter"),
            score = data.tokenNumber("rating"),
            status = when (data.tokenText("state")) {
                "completed" -> 2
                "paused" -> 3
                "dropped" -> 4
                "plan_to_read", "considering" -> 5
                "rereading" -> 6
                else -> 1
            }.toLong(),
            private = data.tokenText("is_private") == "true",
            startedReadingDate = parseDate(data.tokenText("start_date")),
            finishedReadingDate = parseDate(data.tokenText("finish_date")),
        )
    }
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        val existing = findRemote(track)
        val body = buildJsonObject {
            put(
                "state",
                when (TrackStatus.fromValue(track.status)) {
                    TrackStatus.COMPLETED -> "completed"
                    TrackStatus.ON_HOLD -> "paused"
                    TrackStatus.DROPPED -> "dropped"
                    TrackStatus.PLAN_TO_READ -> "plan_to_read"
                    TrackStatus.REREADING -> "rereading"
                    else -> "reading"
                },
            )
            put("is_private", track.private)
            put("progress_chapter", track.lastChapterRead.takeIf { it > 0 })
            put("rating", track.score.takeIf { it > 0 }?.toInt()?.coerceIn(0, 100))
            put("start_date", formatDate(track.startedReadingDate))
            put("finish_date", formatDate(track.finishedReadingDate))
        }
        request("/v1/my/library/${track.remoteId}", if (existing == null) "POST" else "PUT", body)
        return track
    }
    private fun parseDate(value: String?): Long =
        value?.let {
            LocalDate.parse(it.substringBefore('T')).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
            ?: 0
    private fun formatDate(
        value: Long,
    ): String? = value.takeIf {
        it > 0
    }?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
}
