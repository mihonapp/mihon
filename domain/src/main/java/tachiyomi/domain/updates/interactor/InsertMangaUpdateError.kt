package tachiyomi.domain.updates.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

@Inject
class InsertMangaUpdateError(
    private val repository: MangaUpdateErrorRepository,
) {

    suspend fun await(mangaId: Long, errorMessage: String?, timestamp: Long) {
        repository.insert(mangaId, errorMessage, timestamp)
    }
}
