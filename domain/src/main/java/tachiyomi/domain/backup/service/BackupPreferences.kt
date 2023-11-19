package tachiyomi.domain.backup.service

import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.provider.FolderProvider

class BackupPreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {

    fun backupsDirectory() = preferenceStore.getString("backup_directory", folderProvider.path())

    fun backupInterval() = preferenceStore.getInt("backup_interval", 12)

    fun lastAutoBackupTimestamp() = preferenceStore.getLong(Preference.appStateKey("last_auto_backup_timestamp"), 0L)
}
