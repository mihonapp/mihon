package tachiyomi.domain.updates.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

@Inject
class DeleteMangaUpdateError(
    private val repository: MangaUpdateErrorRepository,
) {

    suspend fun await(mangaId: Long) {
        repository.delete(mangaId)
    }

    suspend fun await(mangaIds: List<Long>) {
        if (mangaIds.isEmpty()) return
        repository.deleteByMangaIds(mangaIds)
    }

    suspend fun awaitAll() {
        repository.deleteAll()
    }

    suspend fun awaitNonFavorites() {
        repository.deleteNonFavorites()
    }
}
