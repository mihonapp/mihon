package eu.kanade.tachiyomi.data.track.kitsu.dto

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.kitsu.KitsuApi
import eu.kanade.tachiyomi.data.track.kitsu.KitsuDateHelper
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.Serializable

@Serializable
data class KitsuListSearchResult(
    val data: List<KitsuListSearchItemData>,
    val included: List<KitsuListSearchItemIncluded> = emptyList(),
) {
    fun firstToTrack(): TrackSearch {
        require(data.isNotEmpty()) { "Missing User data from Kitsu" }
        require(included.isNotEmpty()) { "Missing Manga data from Kitsu" }

        val userData = data[0]
        val userDataAttrs = userData.attributes
        val manga = included[0].attributes

        return TrackSearch.create(TrackerManager.KITSU).apply {
            remote_id = userData.id
            title = manga.canonicalTitle
            total_chapters = manga.chapterCount ?: 0
            cover_url = manga.posterImage?.original ?: ""
            summary = manga.synopsis ?: ""
            tracking_url = KitsuApi.mangaUrl(remote_id)
            publishing_status = manga.status
            publishing_type = manga.mangaType ?: ""
            start_date = userDataAttrs.startedAt ?: ""
            started_reading_date = KitsuDateHelper.parse(userDataAttrs.startedAt)
            finished_reading_date = KitsuDateHelper.parse(userDataAttrs.finishedAt)
            status = when (userDataAttrs.status) {
                "current" -> Kitsu.READING
                "completed" -> Kitsu.COMPLETED
                "on_hold" -> Kitsu.ON_HOLD
                "dropped" -> Kitsu.DROPPED
                "planned" -> Kitsu.PLAN_TO_READ
                else -> throw Exception("Unknown status")
            }
            score = userDataAttrs.ratingTwenty?.let { it / 2.0 } ?: 0.0
            last_chapter_read = userDataAttrs.progress.toDouble()
        }
    }
}

@Serializable
data class KitsuListSearchItemData(
    val id: Long,
    val attributes: KitsuListSearchItemDataAttributes,
)

@Serializable
data class KitsuListSearchItemDataAttributes(
    val status: String,
    val startedAt: String?,
    val finishedAt: String?,
    val ratingTwenty: Int?,
    val progress: Int,
)

@Serializable
data class KitsuListSearchItemIncluded(
    val id: Long,
    val attributes: KitsuListSearchItemIncludedAttributes,
)

@Serializable
data class KitsuListSearchItemIncludedAttributes(
    val canonicalTitle: String,
    val chapterCount: Long?,
    val mangaType: String?,
    val posterImage: KitsuSearchItemCover?,
    val synopsis: String?,
    val startDate: String?,
    val status: String,
)
