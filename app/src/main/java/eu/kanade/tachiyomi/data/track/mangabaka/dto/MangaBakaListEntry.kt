package eu.kanade.tachiyomi.data.track.mangabaka.dto

import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaBakaListResult(
    val data: MangaBakaListEntry,
)

@Serializable
data class MangaBakaListEntry(
    val state: String,
    @SerialName("start_date")
    val startDate: String?,
    @SerialName("finish_date")
    val finishDate: String?,
    @SerialName("is_private")
    val isPrivate: Boolean,
    @SerialName("progress_chapter")
    val progressChapter: Double?,
    val rating: Long?,
) {
    fun getStatus(): Long = when (state) {
        "considering" -> MangaBaka.CONSIDERING
        "completed" -> MangaBaka.COMPLETED
        "dropped" -> MangaBaka.DROPPED
        "paused" -> MangaBaka.PAUSED
        "plan_to_read" -> MangaBaka.PLAN_TO_READ
        "reading" -> MangaBaka.READING
        "rereading" -> MangaBaka.REREADING
        else -> throw NotImplementedError("Unknown status: $state")
    }
}
