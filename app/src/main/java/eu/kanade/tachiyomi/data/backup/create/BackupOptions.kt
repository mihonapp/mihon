package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR

data class BackupOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val appSettings: Boolean = true,
    val sourceSettings: Boolean = true,
) {
    fun toBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        appSettings,
        sourceSettings,
    )

    companion object {
        val AutomaticDefaults = BackupOptions(
            libraryEntries = true,
            categories = true,
            chapters = true,
            tracking = true,
            history = true,
            appSettings = true,
            sourceSettings = true,
        )

        fun fromBooleanArray(booleanArray: BooleanArray) = BackupOptions(
            libraryEntries = booleanArray[0],
            categories = booleanArray[1],
            chapters = booleanArray[2],
            tracking = booleanArray[3],
            history = booleanArray[4],
            appSettings = booleanArray[5],
            sourceSettings = booleanArray[6],
        )

        val entries = persistentListOf<BackupOptionEntry>(
            BackupOptionEntry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            BackupOptionEntry(
                label = MR.strings.chapters,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
            ),
            BackupOptionEntry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
            ),
            BackupOptionEntry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
            ),
            BackupOptionEntry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            BackupOptionEntry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
        )
    }
}

data class BackupOptionEntry(
    val label: StringResource,
    val getter: (BackupOptions) -> Boolean,
    val setter: (BackupOptions, Boolean) -> BackupOptions,
)
