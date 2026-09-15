package mihon.desktop.track

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.net.URI
import java.util.UUID

class HikkaTracker(
    baseUrl: String = "https://api.hikka.io",
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
) : JsonTokenTracker(10L, "Hikka", baseUrl, httpClient, "auth", "") {
    override val profilePath = "/user/me"
    override fun account(profile: JsonObject): String? = profile.tokenText("username")
    override suspend fun search(query: String): List<TrackSearchResult> {
        val payload = buildJsonObject {
            for (key in listOf("media_type", "status", "magazines", "genres")) put(key, buildJsonArray { })
            put("only_translated", false)
            put("query", query)
            put(
                "score",
                buildJsonArray {
                    add(0)
                    add(10)
                },
            )
            put(
                "sort",
                buildJsonArray {
                    add("score:desc")
                    add("scored_by:desc")
                },
            )
        }
        return request("/manga?page=1&size=50", "POST", payload).tokenArray("list").map { value ->
            val item = value.jsonObject
            val slug = item.tokenText("slug") ?: throw TrackerApiException("Hikka manga has no slug")
            TrackSearchResult(
                id,
                UUID.nameUUIDFromBytes(slug.toByteArray()).mostSignificantBits and Long.MAX_VALUE,
                item.tokenText("title_ua") ?: item.tokenText("title_en") ?: item.tokenText("title_original").orEmpty(),
                item.tokenNumber("chapters").toLong(),
                item.tokenText("image").orEmpty(),
                "https://hikka.io/manga/$slug",
            )
        }
    }
    private fun slug(track: DesktopTrackRecord): String {
        val uri = URI(track.trackingUrl)
        require(uri.host == "hikka.io" && uri.path.startsWith("/manga/")) { "Hikka tracking URL is required" }
        return uri.rawPath.removePrefix("/manga/").also { require(it.isNotBlank() && '/' !in it) }
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? {
        val data = optional("/read/manga/${slug(track)}") ?: return null
        return track.copy(
            lastChapterRead = data.tokenNumber("chapters"),
            score = data.tokenNumber("score"),
            status = when (data.tokenText("status")) {
                "completed" -> 2
                "on_hold" -> 3
                "dropped" -> 4
                "planned" -> 5
                else -> 1
            }.toLong(),
            startedReadingDate = data.tokenNumber("start_date").toLong() * 1000,
            finishedReadingDate = data.tokenNumber("end_date").toLong() * 1000,
        )
    }
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        val path = "/read/manga/${slug(track)}"
        val previous = optional(path)
        val rereads = maxOf(
            previous?.tokenNumber("rereads")?.toInt() ?: 0,
            if (track.status ==
                TrackStatus.REREADING.value
            ) {
                1
            } else {
                0
            },
        )
        val body = buildJsonObject {
            put("note", previous?.tokenText("note").orEmpty())
            put("chapters", track.lastChapterRead.toInt())
            put("volumes", previous?.tokenNumber("volumes")?.toInt() ?: 0)
            put("rereads", rereads)
            put("score", track.score.toInt().coerceIn(0, 10))
            put(
                "status",
                when (TrackStatus.fromValue(track.status)) {
                    TrackStatus.COMPLETED -> "completed"
                    TrackStatus.ON_HOLD -> "on_hold"
                    TrackStatus.DROPPED -> "dropped"
                    TrackStatus.PLAN_TO_READ -> "planned"
                    else -> "reading"
                },
            )
            put("start_date", track.startedReadingDate.takeIf { it > 0 }?.div(1000))
            put("end_date", track.finishedReadingDate.takeIf { it > 0 }?.div(1000))
        }
        request(path, "PUT", body)
        return track
    }
}
