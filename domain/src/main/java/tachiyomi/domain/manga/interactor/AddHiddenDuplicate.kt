package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRepository

class AddHiddenDuplicate(
    private val mangaRepository: MangaRepository,
) {
    suspend operator fun invoke(id1: Long, id2: Long) {
        mangaRepository.addHiddenDuplicate(id1, id2)
    }
}
