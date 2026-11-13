package mihon.domain.protectedfolder.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.protectedfolder.repository.ProtectedMangaRepository

@Inject
class SetMangaProtected(
    private val repository: ProtectedMangaRepository,
) {

    suspend fun await(mangaIds: List<Long>, protected: Boolean) {
        repository.setProtected(mangaIds, protected)
    }
}
