package eu.kanade.domain.track.store

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

@Inject
@SingleIn(AppScope::class)
class DelayedTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun add(trackId: Long, lastChapterRead: Double) {
        val previousLastChapterRead = preferences.getFloat(trackId.toString(), 0f)
        if (lastChapterRead > previousLastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last chapter read: $lastChapterRead" }
            preferences.edit {
                putFloat(trackId.toString(), lastChapterRead.toFloat())
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
