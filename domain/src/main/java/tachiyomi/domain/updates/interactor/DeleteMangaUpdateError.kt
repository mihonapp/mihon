package tachiyomi.domain.updates.interactor

import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

class DeleteMangaUpdateError(
    private val repository: MangaUpdateErrorRepository,
) {

    suspend fun await(mangaId: Long) {
        repository.delete(mangaId)
    }

    suspend fun awaitAll() {
        repository.deleteAll()
    }

    suspend fun awaitNonFavorites() {
        repository.deleteNonFavorites()
    }
}
