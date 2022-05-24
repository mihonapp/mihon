package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates.Companion.READING_LIST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListItem(
    val series: Series? = null,
    @SerialName("list_id")
    val listId: Int? = null,
    val status: Status? = null,
    val priority: Int? = null,
)

fun ListItem.copyTo(track: Track): Track {
    return track.apply {
        this.status = listId ?: READING_LIST
        this.last_chapter_read = this@copyTo.status?.chapter?.toFloat() ?: 0f
    }
}
