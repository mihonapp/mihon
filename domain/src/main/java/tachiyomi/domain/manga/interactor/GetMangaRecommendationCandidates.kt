package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetMangaRecommendationCandidates(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(sourceId: Long, excludedUrl: String): List<Manga> {
        return mangaRepository.getRecommendationCandidates(sourceId, excludedUrl)
    }
}
