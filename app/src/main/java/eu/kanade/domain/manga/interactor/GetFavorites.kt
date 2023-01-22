package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga

class GetFavorites(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<Manga> {
        return mangaRepository.getFavorites()
    }

    fun subscribe(sourceId: Long): Flow<List<Manga>> {
        return mangaRepository.getFavoritesBySourceId(sourceId)
    }
}
