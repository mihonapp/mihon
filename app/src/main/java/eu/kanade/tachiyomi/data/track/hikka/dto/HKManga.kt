package eu.kanade.tachiyomi.data.track.hikka.dto

import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.hikka.stringToNumber
import eu.kanade.tachiyomi.data.track.hikka.toTrackStatus
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class HKManga(
    @SerialName("data_type")
    val dataType: String,
    @SerialName("title_original")
    val titleOriginal: String,
    @SerialName("media_type")
    val mediaType: String,
    @SerialName("title_ua")
    val titleUa: String? = null,
    @SerialName("title_en")
    val titleEn: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    @SerialName("translated_ua")
    val translatedUa: Boolean,
    val status: String,
    val image: String,
    val year: Int? = null,
    @SerialName("scored_by")
    val scoredBy: Int,
    val score: Double,
    val slug: String,
    @SerialName("start_date")
    val startDate: Long? = null,
    val read: List<HKRead>? = emptyList(),
) {
    fun toTrack(trackId: Long): TrackSearch {
        return TrackSearch.create(trackId).apply {
            remote_id = stringToNumber(this@HKManga.slug)
            title = this@HKManga.titleUa ?: this@HKManga.titleEn ?: this@HKManga.titleOriginal
            total_chapters = this@HKManga.chapters?.toLong() ?: 0
            cover_url = this@HKManga.image
            score = this@HKManga.score
            tracking_url = "${HikkaApi.BASE_URL}/manga/${this@HKManga.slug}"
            publishing_status = this@HKManga.status
            publishing_type = this@HKManga.mediaType

            startDate?.takeIf { it != 0L }?.let {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                start_date = try {
                    outputDf.format(it * 1000)
                } catch (_: Exception) {
                    ""
                }
            }

            val userProgress = read?.firstOrNull()
            if (userProgress != null) {
                status = toTrackStatus(userProgress.status)
                last_chapter_read = userProgress.chapters.toDouble()
                score = userProgress.score.toDouble()
                started_reading_date = (userProgress.startDate ?: 0L) * 1000
                finished_reading_date = (userProgress.endDate ?: 0L) * 1000
            }
        }
    }
}
