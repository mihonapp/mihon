package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaRepository

class ResetViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): Boolean {
        return mangaRepository.resetViewerFlags()
    }
}
