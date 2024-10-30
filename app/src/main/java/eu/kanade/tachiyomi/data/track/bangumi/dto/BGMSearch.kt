package eu.kanade.tachiyomi.data.track.bangumi.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BGMSearchResult(
    val list: List<BGMSearchItem>?,
    val code: Int?,
)

@Serializable
data class BGMSearchItem(
    val id: Long,
    @SerialName("name_cn")
    val nameCn: String,
    val name: String,
    val type: Int,
    val summary: String?,
    val images: BGMSearchItemCovers?,
    @SerialName("eps_count")
    val epsCount: Long?,
    val rating: BGMSearchItemRating?,
    val url: String,
) {
    fun toTrackSearch(trackId: Long): TrackSearch = TrackSearch.create(trackId).apply {
        remote_id = this@BGMSearchItem.id
        title = nameCn.ifBlank { name }
        cover_url = images?.common.orEmpty()
        summary = if (nameCn.isNotBlank()) {
            "作品原名：$name" + this@BGMSearchItem.summary?.let { "\n$it" }.orEmpty()
        } else {
            this@BGMSearchItem.summary.orEmpty()
        }
        score = rating?.score ?: -1.0
        tracking_url = url
        total_chapters = epsCount ?: 0
    }
}

@Serializable
data class BGMSearchItemCovers(
    val common: String?,
)

@Serializable
data class BGMSearchItemRating(
    val score: Double?,
)
