package mihon.domain.sync.model

import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track

/**
 * An entry as another device published it, with what the sync needs on top of a backup to merge it
 * into the library here instead of only adding to it.
 */
data class SyncedManga(
    val manga: Manga,
    val chapters: List<SyncedChapter>,
    /**
     * The categories the entry belongs to over there, or null to leave its categories here as they
     * are: a payload whose categories cannot be resolved on this device says nothing about them.
     */
    val categoryIds: List<Long>?,
    val history: List<RestoredHistory>,
    val tracks: List<Track>,
    val excludedScanlators: List<String>,
    /** When the entry last joined or left the library over there, in seconds. */
    val favoriteChangedAt: Long,
    /** When that device last refreshed the chapter list from the source. */
    val chapterListAt: Long,
)

data class SyncedChapter(
    val chapter: Chapter,
    /** When the reading state was decided over there, in seconds. */
    val readChangedAt: Long,
)
