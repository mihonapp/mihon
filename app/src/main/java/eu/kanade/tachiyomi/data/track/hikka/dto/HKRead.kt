package eu.kanade.tachiyomi.data.track.hikka.dto

import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.hikka.stringToNumber
import eu.kanade.tachiyomi.data.track.hikka.toTrackStatus
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HKRead(
    val reference: String,
    val note: String?,
    val updated: Long,
    val created: Long,
    val status: String,
    val chapters: Int,
    val volumes: Int,
    val rereads: Int,
    val score: Int,
    @SerialName("start_date")
    val startDate: Long? = null,
    @SerialName("end_date")
    val endDate: Long? = null,
    val content: HKManga? = null,
) {
    fun toTrack(trackId: Long): TrackSearch {
        return TrackSearch.create(trackId).apply {
            val mangaContent = this@HKRead.content
            if (mangaContent != null) {
                title = mangaContent.titleUa ?: mangaContent.titleEn ?: mangaContent.titleOriginal
                remote_id = stringToNumber(mangaContent.slug)
                library_id = stringToNumber(mangaContent.slug)
                total_chapters = mangaContent.chapters?.toLong() ?: 0
                tracking_url = "${HikkaApi.BASE_URL}/manga/${mangaContent.slug}"
            }

            last_chapter_read = this@HKRead.chapters.toDouble()
            score = this@HKRead.score.toDouble()
            status = toTrackStatus(this@HKRead.status)

            started_reading_date = startDate?.let { it * 1000 } ?: 0L
            finished_reading_date = endDate?.let { it * 1000 } ?: 0L
        }
    }
}
