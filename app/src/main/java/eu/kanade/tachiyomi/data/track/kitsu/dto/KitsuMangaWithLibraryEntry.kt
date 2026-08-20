package eu.kanade.tachiyomi.data.track.kitsu.dto

import eu.kanade.tachiyomi.data.track.kitsu.toKitsuLocalStatus
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// KitsuManga extended with KitsuLibraryEntryData
@Serializable
data class KitsuMangaWithLibraryEntry(
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
    val myLibraryEntry: KitsuLibraryEntryData?,
) {
    fun toTrackSearch(trackId: Long): TrackSearch? {
        if (myLibraryEntry == null) return null

        return TrackSearch.create(trackId).apply {
            remote_id = this@KitsuMangaWithLibraryEntry.id.toLong()
            library_id = myLibraryEntry.id.toLong()
            title = titles.preferred
            total_chapters = chapterCount ?: 0
            cover_url = posterImage.getPosterUrl()
            summary = description["en"] ?: ""
            tracking_url = "https://kitsu.app/manga/$slug"
            publishing_status = when (this@KitsuMangaWithLibraryEntry.status) {
                "TBA" -> "TBA"
                "CURRENT" -> "Publishing"
                else -> this@KitsuMangaWithLibraryEntry.status.lowercase().replaceFirstChar { it.uppercase() }
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

            started_reading_date = myLibraryEntry.startedAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            finished_reading_date = myLibraryEntry.finishedAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            status = myLibraryEntry.status.toKitsuLocalStatus()
            score = myLibraryEntry.rating?.toDouble() ?: 0.0
            last_chapter_read = myLibraryEntry.progress.toDouble()
            private = myLibraryEntry.private
        }
    }
}

@Serializable
data class KitsuLibraryEntryData(
    val id: String,
    val private: Boolean,
    val progress: Long,
    val rating: Long?,
    val reconsuming: Boolean,
    val status: String,
    val startedAt: String?,
    val finishedAt: String?,
)
