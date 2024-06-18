package eu.kanade.tachiyomi.data.sync.models

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR

data class SyncTriggerOptions(
    val syncOnChapterRead: Boolean = false,
    val syncOnChapterOpen: Boolean = false,
    val syncOnAppStart: Boolean = false,
    val syncOnAppResume: Boolean = false,
) {
    fun asBooleanArray() = booleanArrayOf(
        syncOnChapterRead,
        syncOnChapterOpen,
        syncOnAppStart,
        syncOnAppResume,
    )

    fun anyEnabled() = syncOnChapterRead ||
        syncOnChapterOpen ||
        syncOnAppStart ||
        syncOnAppResume

    companion object {
        val mainOptions = persistentListOf(
            Entry(
                label = MR.strings.sync_on_chapter_read,
                getter = SyncTriggerOptions::syncOnChapterRead,
                setter = { options, enabled -> options.copy(syncOnChapterRead = enabled) },
            ),
            Entry(
                label = MR.strings.sync_on_chapter_open,
                getter = SyncTriggerOptions::syncOnChapterOpen,
                setter = { options, enabled -> options.copy(syncOnChapterOpen = enabled) },
            ),
            Entry(
                label = MR.strings.sync_on_app_start,
                getter = SyncTriggerOptions::syncOnAppStart,
                setter = { options, enabled -> options.copy(syncOnAppStart = enabled) },
            ),
            Entry(
                label = MR.strings.sync_on_app_resume,
                getter = SyncTriggerOptions::syncOnAppResume,
                setter = { options, enabled -> options.copy(syncOnAppResume = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = SyncTriggerOptions(
            syncOnChapterRead = array[0],
            syncOnChapterOpen = array[1],
            syncOnAppStart = array[2],
            syncOnAppResume = array[3],
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (SyncTriggerOptions) -> Boolean,
        val setter: (SyncTriggerOptions, Boolean) -> SyncTriggerOptions,
        val enabled: (SyncTriggerOptions) -> Boolean = { true },
    )
}
