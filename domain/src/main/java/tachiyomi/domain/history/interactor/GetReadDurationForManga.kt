package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.repository.HistoryRepository

@Inject
class GetReadDurationForManga(
    private val repository: HistoryRepository,
) {
    suspend fun await(mangaId: Long): Long {
        return repository.getReadDurationForManga(mangaId)
    }
}
