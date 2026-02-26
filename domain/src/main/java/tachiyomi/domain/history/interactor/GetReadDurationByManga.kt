package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.ReadDurationByManga
import tachiyomi.domain.history.repository.HistoryRepository

class GetReadDurationByManga(
    private val repository: HistoryRepository,
) {
    suspend fun await(): List<ReadDurationByManga> {
        return repository.getReadDurationByManga()
    }
}
