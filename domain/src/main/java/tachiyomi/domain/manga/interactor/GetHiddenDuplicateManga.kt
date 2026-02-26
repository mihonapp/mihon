package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

class GetHiddenDuplicateManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun subscribe(): Flow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> {
        return mangaRepository.getAllHiddenDuplicateManga().map { allHiddenDupesList ->
            allHiddenDupesList.map {
                Pair(it.sourceMangaId, MangaWithChapterCount(it.manga, it.chapterCount))
            }.groupBy(keySelector = {
                mangaRepository.getMangaByIdWithChapterCount(it.first)
            }, valueTransform = { it.second })
        }
    }

    suspend fun invoke(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getHiddenDuplicates(manga)
    }

    suspend fun subscribe(manga: Manga): Flow<List<MangaWithChapterCount>> {
        return mangaRepository.getHiddenDuplicatesAsFlow(manga)
    }
}
