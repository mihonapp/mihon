package mihon.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // User-facing settings. These are ordinary preferences and may travel in a backup.

    fun isEnabled() = preferenceStore.getBoolean("pref_sync_enabled", false)

    /**
     * Hours between background syncs; 0 disables the periodic job.
     */
    fun syncInterval() = preferenceStore.getInt("pref_sync_interval", 0)

    fun syncOnAppLifecycle() = preferenceStore.getBoolean("pref_sync_on_app_lifecycle", true)

    fun syncOnLibraryUpdate() = preferenceStore.getBoolean("pref_sync_on_library_update", true)

    /**
     * Sync as soon as an entry is favourited, removed, or a chapter is left. Affordable only because
     * the library is sharded one file per entry: a single action uploads kilobytes, not the lot.
     */
    fun syncOnAction() = preferenceStore.getBoolean("pref_sync_on_action", true)

    /**
     * Whether extension repositories, source names and the list of installed extensions travel with
     * the library.
     *
     * On by default: without it a device that has just signed in shows entries whose source it
     * cannot name, cannot cover and cannot read. Adopting a repository only makes its catalogue
     * visible — installing an extension from it stays an explicit, per-extension act.
     */
    fun syncExtensionStores() = preferenceStore.getBoolean("pref_sync_extension_stores", true)

    /**
     * Whether the sources you pinned travel with the repositories.
     */
    fun syncPinnedSources() = preferenceStore.getBoolean("pref_sync_pinned_sources", true)

    /**
     * Whether the list of installed extensions travels, so another device can offer them.
     */
    fun syncInstalledExtensions() = preferenceStore.getBoolean("pref_sync_installed_extensions", true)

    // Device-local state. Keyed as app state so it is never written to a backup: these values are
    // meaningless on another device, and the credentials must not end up inside the very files we
    // upload to Drive.

    fun lastSyncAt() = preferenceStore.getLong(appState("pref_sync_last_timestamp"), 0L)

    /**
     * Identifier for this installation, shown in the sync history. Generated once by [SyncDevice].
     */
    fun deviceId() = preferenceStore.getString(appState("pref_sync_device_id"), "")

    /**
     * Cached Drive folder ids, so a sync does not have to resolve the folder tree every round.
     */
    fun rootFolderId() = preferenceStore.getString(appState("pref_sync_root_folder_id"), "")

    fun libraryFolderId() = preferenceStore.getString(appState("pref_sync_library_folder_id"), "")

    fun historyFileId() = preferenceStore.getString(appState("pref_sync_history_file_id"), "")

    fun devicesFileId() = preferenceStore.getString(appState("pref_sync_devices_file_id"), "")

    /**
     * Per root document, the Drive version this device last settled and a digest of its own side of
     * it, as JSON. See [SyncDocuments].
     */
    fun documentState() = preferenceStore.getString(appState("pref_sync_document_state"), "{}")

    /**
     * Every extension repository this device has had or been offered, as JSON. A repository is only
     * adopted from another device the first time it is seen, so one removed here stays removed.
     */
    fun seenExtensionStores() = preferenceStore.getString(appState("pref_sync_seen_extension_stores"), "")

    /**
     * When this device last refreshed its entry in the shared device list.
     */
    fun lastDeviceRecordAt() = preferenceStore.getLong(appState("pref_sync_last_device_record"), 0L)

    /**
     * Last merged extension registry, as JSON. Kept locally so the app can offer the other
     * devices' extensions without a round trip to Drive.
     */
    fun knownExtensions() = preferenceStore.getString(appState("pref_sync_known_extensions"), "")

    /**
     * The shared category list as of the last reconciliation, with the local category behind each
     * id, as JSON. It is what lets the next round tell a rename from a deletion and a creation, and
     * date the changes made here since.
     */
    fun categorySnapshot() = preferenceStore.getString(appState("pref_sync_category_snapshot"), "")

    /**
     * The pinned sources as of the last reconciliation, so the next one can tell whether this device
     * changed them or merely still holds what it was given.
     */
    fun lastPinnedSources() = preferenceStore.getString(appState("pref_sync_last_pinned_sources"), "")

    /**
     * Per-shard bookkeeping, as JSON: file name to the Drive id and version last reconciled.
     *
     * Comparing Drive's own version counter — rather than modification times — is what keeps a
     * device from re-downloading the shards it just uploaded, and sidesteps clock skew between
     * devices entirely.
     */
    fun shardState() = preferenceStore.getString(appState("pref_sync_shard_state"), "{}")

    /**
     * Whether a safety backup was already written before the first sync that can delete entries.
     */
    fun safetyBackupDone() = preferenceStore.getBoolean(appState("pref_sync_safety_backup_done"), false)

    fun accountEmail() = preferenceStore.getString(appState("pref_sync_account_email"), "")

    fun accessToken() = preferenceStore.getString(appState("pref_sync_access_token"), "")

    fun refreshToken() = preferenceStore.getString(appState("pref_sync_refresh_token"), "")

    fun accessTokenExpiresAt() = preferenceStore.getLong(appState("pref_sync_access_token_expires_at"), 0L)

    /**
     * PKCE verifier and CSRF state, kept only between opening the browser and handling the redirect.
     */
    fun pendingCodeVerifier() = preferenceStore.getString(appState("pref_sync_pkce_verifier"), "")

    fun pendingAuthState() = preferenceStore.getString(appState("pref_sync_auth_state"), "")

    /**
     * Forgets everything tied to the remote layout, so the next sync rebuilds it from scratch.
     */
    fun clearRemoteState() {
        rootFolderId().delete()
        libraryFolderId().delete()
        historyFileId().delete()
        devicesFileId().delete()
        categorySnapshot().delete()
        documentState().delete()
        lastDeviceRecordAt().delete()
        shardState().delete()
        lastSyncAt().delete()
    }

    private fun appState(key: String) = Preference.appStateKey(key)
}
