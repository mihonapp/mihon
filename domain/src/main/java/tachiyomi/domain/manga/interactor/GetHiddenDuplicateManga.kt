package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

class GetHiddenDuplicateManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun subscribe(): Flow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> {
        return mangaRepository.getAllHiddenDuplicateManga().map { allHiddenDupesList ->
            allHiddenDupesList.map {
                val keyManga = mangaRepository.getMangaByIdWithChapterCount(it.sourceMangaId)
                Pair(keyManga, MangaWithChapterCount(it.manga, it.chapterCount))
            }.sortedBy { it.second.manga.title.lowercase() }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .toList()
                .sortedBy { it.first.manga.title.lowercase() }
                .sortedByDescending { it.second.count() }
                .toMap()
        }
    }
}
