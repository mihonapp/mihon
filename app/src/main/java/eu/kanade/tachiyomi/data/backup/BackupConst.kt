package eu.kanade.tachiyomi.data.backup

// Filter options
internal object BackupConst {
    const val BACKUP_CATEGORY = 0x1
    const val BACKUP_CATEGORY_MASK = 0x1
    const val BACKUP_CHAPTER = 0x2
    const val BACKUP_CHAPTER_MASK = 0x2
    const val BACKUP_HISTORY = 0x4
    const val BACKUP_HISTORY_MASK = 0x4
    const val BACKUP_TRACK = 0x8
    const val BACKUP_TRACK_MASK = 0x8
    const val BACKUP_ALL = 0xF
}
