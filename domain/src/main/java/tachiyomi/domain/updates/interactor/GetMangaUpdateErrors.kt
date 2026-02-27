package tachiyomi.domain.updates.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.MangaUpdateError
import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

class GetMangaUpdateErrors(
    private val repository: MangaUpdateErrorRepository,
) {

    suspend fun await(): List<MangaUpdateError> {
        return repository.getAll()
    }

    fun subscribe(): Flow<List<MangaUpdateError>> {
        return repository.subscribeAll()
    }
}
