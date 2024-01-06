package tachiyomi.domain.sync

import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import java.util.UUID

class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    object Flags {
        const val NONE = 0x0
        const val SYNC_ON_CHAPTER_READ = 0x1
        const val SYNC_ON_CHAPTER_OPEN = 0x2
        const val SYNC_ON_APP_START = 0x4
        const val SYNC_ON_APP_RESUME = 0x8
        const val SYNC_ON_LIBRARY_UPDATE = 0x10

        const val Defaults = NONE

        fun values() = listOf(
            NONE,
            SYNC_ON_CHAPTER_READ,
            SYNC_ON_CHAPTER_OPEN,
            SYNC_ON_APP_START,
            SYNC_ON_APP_RESUME,
            SYNC_ON_LIBRARY_UPDATE,
        )
    }

    fun syncHost() = preferenceStore.getString("sync_host", "https://sync.tachiyomi.org")
    fun syncAPIKey() = preferenceStore.getString("sync_api_key", "")
    fun lastSyncTimestamp() = preferenceStore.getLong(Preference.appStateKey("last_sync_timestamp"), 0L)

    fun syncInterval() = preferenceStore.getInt("sync_interval", 0)
    fun syncService() = preferenceStore.getInt("sync_service", 0)

    fun googleDriveAccessToken() = preferenceStore.getString(
        Preference.appStateKey("google_drive_access_token"),
        "",
    )

    fun googleDriveRefreshToken() = preferenceStore.getString(
        Preference.appStateKey("google_drive_refresh_token"),
        "",
    )

    fun uniqueDeviceID(): String {
        val uniqueIDPreference = preferenceStore.getString("unique_device_id", "")

        // Retrieve the current value of the preference
        var uniqueID = uniqueIDPreference.get()
        if (uniqueID.isBlank()) {
            uniqueID = UUID.randomUUID().toString()
            uniqueIDPreference.set(uniqueID)
        }

        return uniqueID
    }

    fun syncFlags() = preferenceStore.getInt("sync_flags", Flags.Defaults)

    fun isSyncEnabled(): Boolean {
        return syncService().get() != 0
    }
}
