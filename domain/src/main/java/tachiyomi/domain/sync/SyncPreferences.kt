package tachiyomi.domain.sync

import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.Preference

class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun syncHost() = preferenceStore.getString("sync_host", "https://sync.tachiyomi.org")
    fun syncAPIKey() = preferenceStore.getString("sync_api_key", "")
    fun lastSyncTimestamp() = preferenceStore.getLong(Preference.appStateKey("last_sync_timestamp"), 0L)

    fun syncInterval() = preferenceStore.getInt("sync_interval", 0)

    fun deviceName() = preferenceStore.getString(
        Preference.appStateKey("device_name"),
        android.os.Build.MANUFACTURER + android.os.Build.PRODUCT,
    )

    fun syncService() = preferenceStore.getInt("sync_service", 0)

    private fun googleDriveAccessToken() = preferenceStore.getString(Preference.appStateKey("google_drive_access_token"), "")

    fun setGoogleDriveAccessToken(accessToken: String) {
        googleDriveAccessToken().set(accessToken)
    }

    fun getGoogleDriveAccessToken() = googleDriveAccessToken().get()

    private fun googleDriveRefreshToken() = preferenceStore.getString(Preference.appStateKey("google_drive_refresh_token"), "")

    fun setGoogleDriveRefreshToken(refreshToken: String) {
        googleDriveRefreshToken().set(refreshToken)
    }

    fun getGoogleDriveRefreshToken() = googleDriveRefreshToken().get()
}
