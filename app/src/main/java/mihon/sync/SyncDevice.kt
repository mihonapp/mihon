package mihon.sync

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.UUID

/**
 * Stable identity for this installation, used to attribute entries in the sync history.
 *
 * The identifier is generated once and kept in device-local state, so it survives app restarts but
 * never travels in a backup — two devices restored from the same file must not claim to be the same
 * device, or the history becomes a lie.
 */
@Inject
@SingleIn(AppScope::class)
class SyncDevice(
    private val syncPreferences: SyncPreferences,
) {

    val id: String
        get() {
            val stored = syncPreferences.deviceId().get()
            if (stored.isNotBlank()) return stored

            return UUID.randomUUID().toString().also { syncPreferences.deviceId().set(it) }
        }

    /**
     * Human-readable label shown in the history. Falls back to the model alone when the
     * manufacturer is already part of it, which is common on Xiaomi and Samsung builds.
     */
    val name: String
        get() {
            val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()

            return when {
                model.isEmpty() -> manufacturer.ifEmpty { "Android" }
                manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true) -> model
                else -> "$manufacturer $model"
            }
        }
}
