package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaRepository
import tachiyomi.domain.manga.model.Manga

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(title: String): Manga? {
        return mangaRepository.getDuplicateLibraryManga(title.lowercase())
    }
}
