package tachiyomi.domain.backup.repository

import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate

interface RestoreRepository {

    /**
     * Restores [entries] all together: either every one of them is restored or none is. [update] is what to change on each
     * entry once its chapters and history are in place, and is part of the same all-or-nothing restore.
     */
    suspend fun restoreManga(entries: List<RestoredManga>, update: suspend (Manga) -> MangaUpdate)
}
