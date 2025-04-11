package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRepository

class RemoveHiddenDuplicates (
    private val mangaRepository: MangaRepository
) {

    suspend operator fun invoke(id1: Long, id2: Long) {
        return mangaRepository.removeHiddenDuplicates(id1, id2)
    }
}
