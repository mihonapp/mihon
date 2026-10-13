package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.model.ReadDurationByManga
import tachiyomi.domain.history.repository.HistoryRepository

@Inject
class GetReadDurationByManga(
    private val repository: HistoryRepository,
) {
    suspend fun await(): List<ReadDurationByManga> {
        return repository.getReadDurationByManga()
    }
}
