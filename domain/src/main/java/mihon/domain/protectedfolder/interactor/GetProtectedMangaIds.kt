package mihon.domain.protectedfolder.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import mihon.domain.protectedfolder.repository.ProtectedMangaRepository

@Inject
class GetProtectedMangaIds(
    private val repository: ProtectedMangaRepository,
) {

    suspend fun await(): Set<Long> {
        return repository.getProtectedMangaIds()
    }

    fun subscribe(): Flow<Set<Long>> {
        return repository.getProtectedMangaIdsAsFlow()
    }
}
