package eu.kanade.tachiyomi.data.track.bangumi.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BangumiSearchResult(
    val list: List<BangumiSearchItem>?,
    val code: Int?,
)

@Serializable
data class BangumiSearchItem(
    val id: Long,
    @SerialName("name_cn")
    val nameCn: String,
    val name: String,
    val type: Int,
    val images: BangumiSearchItemCovers?,
    @SerialName("eps_count")
    val epsCount: Long?,
    val rating: BangumiSearchItemRating?,
    val url: String,
) {
    fun toTrackSearch(trackId: Long): TrackSearch = TrackSearch.create(trackId).apply {
        remote_id = this@BangumiSearchItem.id
        title = nameCn
        cover_url = images?.common ?: ""
        summary = this@BangumiSearchItem.name
        score = rating?.score ?: -1.0
        tracking_url = url
        total_chapters = epsCount ?: 0
    }
}

@Serializable
data class BangumiSearchItemCovers(
    val common: String?,
)

@Serializable
data class BangumiSearchItemRating(
    val score: Double?,
)
