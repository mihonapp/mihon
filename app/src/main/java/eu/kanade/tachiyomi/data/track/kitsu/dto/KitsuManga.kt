package eu.kanade.tachiyomi.data.track.kitsu.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.Serializable

@Serializable
data class KitsuManga(
    val id: String,
    val titles: KitsuMangaTitles,
    val chapterCount: Long?,
    val staff: KitsuMangaStaffData,
    val posterImage: KitsuMangaPosters,
    val description: Map<String, String>,
    val status: String,
    val subtype: String,
    val startDate: String?,
    val endDate: String?,
    val slug: String,
    val averageRating: Double?,
) {
    fun toTrackSearch(trackId: Long): TrackSearch {
        return TrackSearch.create(trackId).apply {
            remote_id = this@KitsuManga.id.toLong()
            title = titles.preferred
            total_chapters = chapterCount ?: 0
            cover_url = posterImage.getPosterUrl()
            summary = description["en"] ?: ""
            tracking_url = "https://kitsu.app/manga/$slug"
            score = averageRating ?: -1.0
            publishing_status = when (this@KitsuManga.status) {
                "TBA" -> "TBA"
                "CURRENT" -> "Publishing"
                else -> this@KitsuManga.status.lowercase().replaceFirstChar { it.uppercase() }
            }
            publishing_type = if (subtype != "OEL") {
                subtype.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                subtype
            }
            start_date = startDate ?: ""
            authors = staff.nodes
                .filter { it.role.contains("Story") }
                .map { it.person.name }
            artists = staff.nodes
                .filter { it.role.contains("Art") }
                .map { it.person.name }
        }
    }
}

@Serializable
data class KitsuMangaTitles(
    val preferred: String,
)

@Serializable
data class KitsuMangaStaffData(
    val nodes: List<KitsuMangaStaff>,
)

@Serializable
data class KitsuMangaStaff(
    val role: String,
    val person: KitsuMangaStaffPerson,
)

@Serializable
data class KitsuMangaStaffPerson(
    val name: String,
)

@Serializable
data class KitsuMangaPosters(
    val views: List<KitsuMangaPoster>,
    val original: KitsuMangaPoster,
) {
    // we only ask for the "small" poster in the query
    fun getPosterUrl(): String = views.firstOrNull()?.url ?: original.url
}

@Serializable
data class KitsuMangaPoster(
    val name: String,
    val url: String,
)
