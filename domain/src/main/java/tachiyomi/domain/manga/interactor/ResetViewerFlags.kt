package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRepository

class ResetViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): Boolean {
        return mangaRepository.resetViewerFlags()
    }
}
