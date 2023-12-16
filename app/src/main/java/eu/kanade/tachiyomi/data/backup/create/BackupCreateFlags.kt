package eu.kanade.tachiyomi.data.backup.create

internal object BackupCreateFlags {
    const val BACKUP_CATEGORY = 0x1
    const val BACKUP_CHAPTER = 0x2
    const val BACKUP_HISTORY = 0x4
    const val BACKUP_TRACK = 0x8
    const val BACKUP_APP_PREFS = 0x10
    const val BACKUP_SOURCE_PREFS = 0x20

    const val AutomaticDefaults = BACKUP_CATEGORY or
        BACKUP_CHAPTER or
        BACKUP_HISTORY or
        BACKUP_TRACK or
        BACKUP_APP_PREFS or
        BACKUP_SOURCE_PREFS
}
