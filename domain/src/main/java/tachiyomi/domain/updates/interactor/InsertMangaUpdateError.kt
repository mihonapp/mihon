package tachiyomi.domain.updates.interactor

import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

class InsertMangaUpdateError(
    private val repository: MangaUpdateErrorRepository,
) {

    suspend fun await(mangaId: Long, errorMessage: String?, timestamp: Long) {
        repository.insert(mangaId, errorMessage, timestamp)
    }
}
