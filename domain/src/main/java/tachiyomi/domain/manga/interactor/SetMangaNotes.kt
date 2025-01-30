package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaNotes(
    private val mangaRepository: MangaRepository,
) {

    suspend fun awaitSetNotes(mangaId: Long, notes: String): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                notes = notes,
            ),
        )
    }
}
