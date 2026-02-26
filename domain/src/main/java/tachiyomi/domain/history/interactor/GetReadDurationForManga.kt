package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.HistoryRepository

class GetReadDurationForManga(
    private val repository: HistoryRepository,
) {
    suspend fun await(mangaId: Long): Long {
        return repository.getReadDurationForManga(mangaId)
    }

    suspend fun await(mangaId: Long, title: String): Long {
        return repository.getReadDurationForMangaByTitle(title)
    }
}
