package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaRepository

class UpdateMangaLastUpdate(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, lastUpdate: Long) {
        mangaRepository.updateLastUpdate(mangaId, lastUpdate)
    }
}
