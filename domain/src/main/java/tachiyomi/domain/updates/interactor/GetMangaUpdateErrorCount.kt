package tachiyomi.domain.updates.interactor

import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

class GetMangaUpdateErrorCount(
    private val repository: MangaUpdateErrorRepository,
) {

    suspend fun await(): Long {
        return repository.getCount()
    }
}
