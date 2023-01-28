package eu.kanade.domain.track.store

import android.content.Context
import androidx.core.content.edit
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.model.Track

class DelayedTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun addItem(track: Track) {
        val trackId = track.id.toString()
        val lastChapterRead = preferences.getFloat(trackId, 0f)
        if (track.lastChapterRead > lastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last chapter read: ${track.lastChapterRead}" }
            preferences.edit {
                putFloat(trackId, track.lastChapterRead.toFloat())
            }
        }
    }

    fun remove(trackId: Long) {
        preferences.edit {
            remove(trackId.toString())
        }
    }

    fun getItems(): List<DelayedTrackingItem> {
        return preferences.all.mapNotNull {
            DelayedTrackingItem(
                trackId = it.key.toLong(),
                lastChapterRead = it.value.toString().toFloat(),
            )
        }
    }

    data class DelayedTrackingItem(
        val trackId: Long,
        val lastChapterRead: Float,
    )
}
