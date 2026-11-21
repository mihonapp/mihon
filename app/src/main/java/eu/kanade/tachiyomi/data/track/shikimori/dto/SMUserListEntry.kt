package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMUserListResult(
    val data: SMUserListEntries,
)

@Serializable
data class SMUserListEntries(
    val mangas: List<SMUserListManga>,
)

@Serializable
data class SMUserListManga(
    val id: String,
    val url: String,
    val name: String,
    @SerialName("chapters")
    val totalChapters: Long, // the title's total chapters
    val userRate: SMUserRate?,
) {
    fun toTrack(trackId: Long): Track {
        return Track.create(trackId).apply {
            title = name
            total_chapters = totalChapters
            tracking_url = url
            if (userRate != null) {
                // null if not in user's list, must not throw here because it'd break adding titles
                // throws in the findLibManga method of ShikimoriApi if null and shouldn't be
                remote_id = userRate.rateId.toLong()
                library_id = userRate.rateId.toLong()
                last_chapter_read = userRate.chapters.toDouble()
                score = userRate.score
                status = toTrackStatus(userRate.status)
            }
        }
    }
}

@Serializable
data class SMUserRate(
    @SerialName("id")
    val rateId: String, // ID of the list entry (NOT the title)
    val chapters: Long, // the user's chapter progress
    val status: String,
    val score: Double,
)
