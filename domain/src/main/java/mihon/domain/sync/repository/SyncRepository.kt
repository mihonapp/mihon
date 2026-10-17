package mihon.domain.sync.repository

import mihon.domain.sync.model.SyncMangaState
import mihon.domain.sync.model.SyncedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate

interface SyncRepository {

    /** The state of every entry the sync tracks anything about, by entry id. */
    suspend fun getStates(): Map<Long, SyncMangaState>

    /** When the reading state of each chapter of [mangaId] was decided, by chapter url, in seconds. */
    suspend fun getReadChangedAt(mangaId: Long): Map<String, Long>

    /**
     * Merges [entries] into the library all together: either every one of them is merged or none is.
     *
     * Unlike a restore, which only ever adds, both sides are live devices here, so the most recent
     * decision wins, including a removal. [update] is what to change on each entry once merged, as for
     * a restore.
     */
    suspend fun merge(entries: List<SyncedManga>, update: suspend (Manga) -> MangaUpdate)
}
