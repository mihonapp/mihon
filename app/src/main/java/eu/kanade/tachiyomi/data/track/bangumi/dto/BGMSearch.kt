package eu.kanade.tachiyomi.data.track.bangumi.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BGMSearchResult(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val data: List<BGMSubject> = emptyList(),
)

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMSubject(
    val id: Long,
    @SerialName("name_cn")
    val nameCn: String,
    val name: String,
    val summary: String?,
    val date: String?, // YYYY-MM-DD
    val images: BGMSubjectImages?,
    val volumes: Long = 0,
    val eps: Long = 0,
    val rating: BGMSubjectRating?,
    val platform: String?,
) {
    fun toTrackSearch(trackId: Long): TrackSearch = TrackSearch.create(trackId).apply {
        remote_id = this@BGMSubject.id
        title = nameCn.ifBlank { name }
        cover_url = images?.common.orEmpty()
        summary = if (nameCn.isNotBlank()) {
            "作品原名：$name" + this@BGMSubject.summary?.let { "\n${it.trim()}" }.orEmpty()
        } else {
            this@BGMSubject.summary?.trim().orEmpty()
        }
        score = rating?.score ?: -1.0
        tracking_url = "https://bangumi.tv/subject/${this@BGMSubject.id}"
        total_chapters = eps
        start_date = date ?: ""
    }
}

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMSubjectImages(
    val common: String?,
)

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMSubjectRating(
    val score: Double?,
)
