package tachiyomi.domain.sync.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class SyncPreferences(
    preferenceStore: PreferenceStore,
) {

    val syncService: Preference<Int> = preferenceStore.getInt("sync_service", SyncService.NONE.ordinal)

    val syncInterval: Preference<Int> = preferenceStore.getInt("sync_interval", 0)

    val lastSyncTimestamp: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_sync_timestamp"),
        0L,
    )

    /**
     * Credentials of the Google OAuth client, entered by the user in the sync settings. Google has
     * no way to reach a Drive without one, but it does not have to be known at build time.
     */
    val googleDriveClientId: Preference<String> = preferenceStore.getString(
        Preference.privateKey("google_drive_client_id"),
        "",
    )

    val googleDriveClientSecret: Preference<String> = preferenceStore.getString(
        Preference.privateKey("google_drive_client_secret"),
        "",
    )

    val googleDriveAccessToken: Preference<String> = preferenceStore.getString(
        Preference.privateKey("google_drive_access_token"),
        "",
    )

    val googleDriveRefreshToken: Preference<String> = preferenceStore.getString(
        Preference.privateKey("google_drive_refresh_token"),
        "",
    )

    /** Epoch millis at which [googleDriveAccessToken] stops being valid. */
    val googleDriveTokenExpiry: Preference<Long> = preferenceStore.getLong(
        Preference.privateKey("google_drive_token_expiry"),
        0L,
    )

    /** Drive file id of the remote snapshot, cached to avoid a lookup on every sync. */
    val googleDriveFileId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("google_drive_file_id"),
        "",
    )

    fun hasGoogleDriveCredentials(): Boolean =
        googleDriveClientId.get().isNotBlank() && googleDriveClientSecret.get().isNotBlank()

    fun isGoogleDriveLoggedIn(): Boolean = googleDriveRefreshToken.get().isNotBlank()

    /** Signs out but keeps the OAuth client credentials, which are not tied to an account. */
    fun logoutGoogleDrive() {
        googleDriveAccessToken.delete()
        googleDriveRefreshToken.delete()
        googleDriveTokenExpiry.delete()
        googleDriveFileId.delete()
        syncService.set(SyncService.NONE.ordinal)
    }
}

enum class SyncService {
    NONE,
    GOOGLE_DRIVE,
    ;

    companion object {
        fun fromInt(value: Int) = entries.getOrElse(value) { NONE }
    }
}
