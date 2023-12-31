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
    val privateSettings: Boolean = false,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        appSettings,
        sourceSettings,
        privateSettings,
    )

    fun anyEnabled() = libraryEntries || appSettings || sourceSettings

    companion object {
        val libraryOptions = persistentListOf(
            Entry(
                label = MR.strings.manga,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.chapters,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries },
            ),
        )

        val settingsOptions = persistentListOf(
            Entry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.private_settings,
                getter = BackupOptions::privateSettings,
                setter = { options, enabled -> options.copy(privateSettings = enabled) },
                enabled = { it.appSettings || it.sourceSettings },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = BackupOptions(
            libraryEntries = array[0],
            categories = array[1],
            chapters = array[2],
            tracking = array[3],
            history = array[4],
            appSettings = array[5],
            sourceSettings = array[6],
            privateSettings = array[7],
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
