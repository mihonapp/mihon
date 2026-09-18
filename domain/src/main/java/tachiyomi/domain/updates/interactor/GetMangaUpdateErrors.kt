package tachiyomi.domain.updates.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga
import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

@Inject
class GetMangaUpdateErrors(
    private val repository: MangaUpdateErrorRepository,
) {

    fun subscribeCount(): Flow<Long> {
        return repository.subscribeCount()
    }

    fun subscribeWithManga(): Flow<List<MangaUpdateErrorWithManga>> {
        return repository.subscribeWithManga()
    }
}
