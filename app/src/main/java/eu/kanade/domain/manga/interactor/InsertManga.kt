package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository

class InsertManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(manga: Manga): Long? {
        return mangaRepository.insert(manga)
    }
}
