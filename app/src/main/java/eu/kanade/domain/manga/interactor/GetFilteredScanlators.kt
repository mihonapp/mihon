package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.repository.MangaRepository

@Inject
class GetFilteredScanlators(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return mangaRepository.getFilteredScanlators(mangaId)
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return mangaRepository.getFilteredScanlatorsAsFlow(mangaId)
    }
}
