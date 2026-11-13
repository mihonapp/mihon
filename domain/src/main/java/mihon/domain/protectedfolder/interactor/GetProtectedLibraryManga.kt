package mihon.domain.protectedfolder.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import mihon.domain.protectedfolder.repository.ProtectedMangaRepository
import tachiyomi.domain.library.model.LibraryManga

@Inject
class GetProtectedLibraryManga(
    private val repository: ProtectedMangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return repository.getProtectedLibraryManga()
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return repository.getProtectedLibraryMangaAsFlow()
    }
}
