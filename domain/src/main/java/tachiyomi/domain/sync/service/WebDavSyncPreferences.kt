package tachiyomi.domain.sync.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
class WebDavSyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    val syncEnabled: Preference<Boolean> = preferenceStore.getBoolean("webdav_sync_enabled", false)

    val serverUrl: Preference<String> = preferenceStore.getString(Preference.privateKey("webdav_url"), "")

    val username: Preference<String> = preferenceStore.getString(Preference.privateKey("webdav_username"), "")

    val password: Preference<String> = preferenceStore.getString(Preference.privateKey("webdav_password"), "")

    val remotePath: Preference<String> = preferenceStore.getString("webdav_remote_path", "/mihon")

    val syncIntervalMinutes: Preference<Int> = preferenceStore.getInt("webdav_sync_interval_minutes", 60)

    val syncOnReaderExit: Preference<Boolean> = preferenceStore.getBoolean("webdav_sync_on_reader_exit", true)

    val syncOnAppStart: Preference<Boolean> = preferenceStore.getBoolean("webdav_sync_on_app_start", false)

    val syncWifiOnly: Preference<Boolean> = preferenceStore.getBoolean("webdav_sync_wifi_only", false)

    val syncLibrary: Preference<Boolean> = preferenceStore.getBoolean("webdav_sync_library", true)

    val syncExtensionStores: Preference<Boolean> = preferenceStore.getBoolean("webdav_sync_extension_stores", true)

    val syncExtensions: Preference<Boolean> = preferenceStore.getBoolean("webdav_sync_extensions", true)

    val deviceId: Preference<String> = preferenceStore.getString(Preference.appStateKey("webdav_device_id"), "")

    val lastSyncTimestamp: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("webdav_last_sync_timestamp"),
        0L,
    )

    val lastSyncEtag: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("webdav_last_sync_etag"),
        "",
    )

    val lastSyncStatus: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("webdav_last_sync_status"),
        "",
    )

    val missingExtensionsJson: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("webdav_missing_extensions"),
        "[]",
    )

    fun getOrCreateDeviceId(): String {
        val existing = deviceId.get()
        if (existing.isNotBlank()) return existing
        val created = UUID.randomUUID().toString()
        deviceId.set(created)
        return created
    }

    fun isConfigured(): Boolean = serverUrl.get().isNotBlank()
}
