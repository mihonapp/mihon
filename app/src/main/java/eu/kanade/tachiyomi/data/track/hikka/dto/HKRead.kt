package eu.kanade.tachiyomi.data.track.hikka.dto

import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.hikka.stringToNumber
import eu.kanade.tachiyomi.data.track.hikka.toTrackStatus
import eu.kanade.tachiyomi.data.track.model.TrackSearch
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
    val content: HKManga,
) {
    fun toTrack(trackId: Long): TrackSearch {
        return TrackSearch.create(trackId).apply {
            title = this@HKRead.content.titleUa ?: this@HKRead.content.titleEn ?: this@HKRead.content.titleOriginal
            remote_id = stringToNumber(this@HKRead.content.slug)
            total_chapters = this@HKRead.content.chapters?.toLong() ?: 0
            library_id = stringToNumber(this@HKRead.content.slug)
            last_chapter_read = this@HKRead.chapters.toDouble()
            score = this@HKRead.score.toDouble()
            status = toTrackStatus(this@HKRead.status)
            tracking_url = HikkaApi.BASE_URL + "/manga/${this@HKRead.content.slug}"
        }
    }
}
