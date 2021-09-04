package eu.kanade.tachiyomi.data.track.job

import android.content.Context
import androidx.core.content.edit
import eu.kanade.tachiyomi.data.database.models.Track
import timber.log.Timber

class DelayedTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun addItem(track: Track) {
        val trackId = track.id.toString()
        val (_, lastChapterRead) = preferences.getString(trackId, "0:0.0")!!.split(":")
        if (track.last_chapter_read > lastChapterRead.toFloat()) {
            val value = "${track.manga_id}:${track.last_chapter_read}"
            Timber.i("Queuing track item: $trackId, $value")
            preferences.edit {
                putString(trackId, value)
            }
        }
    }

    fun clear() {
        preferences.edit {
            clear()
        }
    }

    fun getItems(): List<DelayedTrackingItem> {
        return (preferences.all as Map<String, String>).entries
            .map {
                val (mangaId, lastChapterRead) = it.value.split(":")
                DelayedTrackingItem(
                    trackId = it.key.toLong(),
                    mangaId = mangaId.toLong(),
                    lastChapterRead = lastChapterRead.toFloat(),
                )
            }
    }

    data class DelayedTrackingItem(
        val trackId: Long,
        val mangaId: Long,
        val lastChapterRead: Float,
    )
}
