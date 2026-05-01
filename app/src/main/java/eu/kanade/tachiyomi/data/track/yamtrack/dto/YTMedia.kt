package eu.kanade.tachiyomi.data.track.yamtrack.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.yamtrack.Yamtrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YTSearchResponse(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<YTSearchItem> = emptyList(),
)

@Serializable
data class YTSearchItem(
    @SerialName("media_id")
    val mediaId: String = "",
    val source: String = "",
    val title: String = "",
    val image: String? = null,
    @SerialName("media_type")
    val mediaType: String? = null,
    val description: String? = null,
    // Source/upstream score (0-10) when the search endpoint provides one.
    val score: Double? = null,
)

@Serializable
data class YTMediaItem(
    @SerialName("media_id")
    val mediaId: String? = null,
    val source: String? = null,
    val title: String? = null,
    val image: String? = null,
    @SerialName("media_type")
    val mediaType: String? = null,
    val synopsis: String? = null,
    val tracked: Boolean = false,
    @SerialName("max_progress")
    val maxProgress: Int? = null,
    // Source/upstream score (0-10), separate from the user's `consumption.score`.
    val score: Double? = null,
    val consumptions: List<YTConsumption> = emptyList(),
)

@Serializable
data class YTConsumption(
    val status: Int? = null,
    val progress: Int? = null,
    val score: Double? = null,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("end_date")
    val endDate: String? = null,
)

fun YTSearchItem.toTrackSearch(trackerId: Long, baseUrl: String): TrackSearch {
    val item = this
    return TrackSearch.create(trackerId).apply {
        remote_id = Yamtrack.buildRemoteId(item.source, item.mediaId)
        title = item.title
        cover_url = item.image.orEmpty()
        summary = composeSummary(item.score, item.description)
        tracking_url = Yamtrack.buildTrackingUrl(baseUrl, item.source, item.mediaId, item.title)
        publishing_type = item.mediaType.orEmpty()
    }
}

/**
 * Search results from Yamtrack only carry id/title/image/media_type. The detail endpoint
 * adds synopsis and source score, so we layer those onto the existing TrackSearch (without
 * clobbering fields the search response already populated, like cover or title).
 */
fun TrackSearch.applyDetail(detail: YTMediaItem): TrackSearch = apply {
    if (cover_url.isBlank()) cover_url = detail.image.orEmpty()
    summary = composeSummary(detail.score, detail.synopsis)
}

fun YTMediaItem.copyToTrack(track: Track) {
    val consumption = consumptions.firstOrNull()
    track.status = Yamtrack.statusFromApi(consumption?.status)
    track.last_chapter_read = consumption?.progress?.toDouble() ?: track.last_chapter_read
    track.score = consumption?.score ?: 0.0
    track.total_chapters = resolveTotalChapters(maxProgress)
    consumption?.startDate?.let { track.started_reading_date = Yamtrack.parseIsoDate(it) }
    consumption?.endDate?.let { track.finished_reading_date = Yamtrack.parseIsoDate(it) }
}

private fun composeSummary(sourceScore: Double?, description: String?): String {
    val scoreLine = sourceScore
        ?.takeIf { it > 0.0 }
        ?.let { "★ %.1f / 10".format(it) }
    val body = description?.trim().orEmpty()
    return when {
        scoreLine != null && body.isNotEmpty() -> "$scoreLine\n\n$body"
        scoreLine != null -> scoreLine
        else -> body
    }
}

/**
 * Yamtrack's API falls back to `max_progress = 1` when the upstream provider reports no
 * chapter count (typical for ongoing manga). The UI treats `total_chapters > 0` as a hard
 * upper bound for the progress picker, so a placeholder `1` would cap users at chapter 1.
 * Map `max_progress <= 1` to `0` ("unknown"), which lets the picker go up to 10000.
 */
internal fun resolveTotalChapters(maxProgress: Int?): Long =
    if (maxProgress != null && maxProgress > 1) maxProgress.toLong() else 0L
