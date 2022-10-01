package eu.kanade.domain.manga.interactor

import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getLibraryManga()
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryMangaAsFlow()
    }
}
