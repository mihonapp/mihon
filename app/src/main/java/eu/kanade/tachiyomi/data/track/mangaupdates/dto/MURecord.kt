package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MURecord(
    @SerialName("series_id")
    val seriesId: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    val image: MUImage? = null,
    val type: String? = null,
    val year: String? = null,
    @SerialName("bayesian_rating")
    val bayesianRating: Double? = null,
    @SerialName("rating_votes")
    val ratingVotes: Int? = null,
    @SerialName("latest_chapter")
    val latestChapter: Int? = null,
    val authors: List<MUAuthor> = emptyList(),
) {
    fun toTrackSearch(id: Long): TrackSearch {
        return TrackSearch.create(id).apply {
            remote_id = this@MURecord.seriesId ?: 0L
            title = this@MURecord.title?.htmlDecode() ?: ""
            total_chapters = 0
            cover_url = this@MURecord.image?.url?.original ?: ""
            summary = this@MURecord.description?.htmlDecode() ?: ""
            tracking_url = this@MURecord.url ?: ""
            publishing_status = ""
            publishing_type = this@MURecord.type.toString()
            start_date = this@MURecord.year.toString()
            score = this@MURecord.bayesianRating?.takeIf { it > 0 } ?: -1.0
            authors = this@MURecord.authors.filter { it.type == "Author" }.map { it.name }
            artists = this@MURecord.authors.filter { it.type == "Artist" }.map { it.name }
        }
    }
}
