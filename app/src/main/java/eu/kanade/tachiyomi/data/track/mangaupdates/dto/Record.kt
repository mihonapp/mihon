package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Record(
    @SerialName("series_id")
    val seriesId: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    val image: Image? = null,
    val type: String? = null,
    val year: String? = null,
    @SerialName("bayesian_rating")
    val bayesianRating: Double? = null,
    @SerialName("rating_votes")
    val ratingVotes: Int? = null,
    @SerialName("latest_chapter")
    val latestChapter: Int? = null,
)

fun Record.toTrackSearch(id: Long): TrackSearch {
    return TrackSearch.create(id).apply {
        remote_id = this@toTrackSearch.seriesId ?: 0L
        title = this@toTrackSearch.title?.htmlDecode() ?: ""
        total_chapters = 0
        cover_url = this@toTrackSearch.image?.url?.original ?: ""
        summary = this@toTrackSearch.description?.htmlDecode() ?: ""
        tracking_url = this@toTrackSearch.url ?: ""
        publishing_status = ""
        publishing_type = this@toTrackSearch.type.toString()
        start_date = this@toTrackSearch.year.toString()
    }
}
