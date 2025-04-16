package tachiyomi.domain.manga.interactor

import tachiyomi.domain.hiddenDuplicates.repository.HiddenDuplicateRepository
import tachiyomi.domain.manga.model.HiddenDuplicate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

class GetHiddenDuplicates(
    private val mangaRepository: MangaRepository,
    private val hiddenDuplicateRepository: HiddenDuplicateRepository,
) {

    suspend operator fun invoke(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getHiddenDuplicates(manga)
    }

    suspend fun await(): List<HiddenDuplicate> {
        return hiddenDuplicateRepository.getAll()
    }
}
