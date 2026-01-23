package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetFavoritesEntry(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<Manga> {
        return mangaRepository.getFavoritesEntry()
    }

    fun subscribe(sourceId: Long): Flow<List<Manga>> {
        return mangaRepository.getFavoritesEntryBySourceId(sourceId)
    }
}
