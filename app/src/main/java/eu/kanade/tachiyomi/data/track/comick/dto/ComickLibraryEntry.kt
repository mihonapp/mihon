package eu.kanade.tachiyomi.data.track.comick.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.comick.asHidToLong
import eu.kanade.tachiyomi.data.track.comick.fromApiListStatus
import kotlinx.serialization.Serializable

@Serializable
data class ComickLibraryEntry(
    val data: ComickLibraryEntryItem,
) {
    fun toTrack(trackerId: Long): Track {
        return Track.create(trackerId).apply {
            remote_id = data.hid.asHidToLong()
            title = data.title
            status = data.status.fromApiListStatus()
            last_chapter_read = data.progress?.number?.toDouble() ?: 0.0
            score = data.rating?.toDouble() ?: 0.0
        }
    }
}

@Serializable
data class ComickLibraryEntryItem(
    val hid: String,
    val title: String,
    val status: Long,
    val progress: ComickLibraryEntryProgress?,
    val rating: Int?,
)

@Serializable
data class ComickLibraryEntryProgress(
    val number: String?,
)
