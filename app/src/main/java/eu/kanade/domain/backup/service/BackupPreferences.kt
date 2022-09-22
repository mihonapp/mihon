package eu.kanade.domain.backup.service

import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.provider.FolderProvider

class BackupPreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {

    fun backupsDirectory() = preferenceStore.getString("backup_directory", folderProvider.path())

    fun numberOfBackups() = preferenceStore.getInt("backup_slots", 2)

    fun backupInterval() = preferenceStore.getInt("backup_interval", 12)
}
