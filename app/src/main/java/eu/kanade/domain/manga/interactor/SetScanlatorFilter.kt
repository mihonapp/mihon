package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.MangaRepository

@Inject
class SetScanlatorFilter(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, scanlators: Set<String>, include: Boolean) {
        mangaRepository.setScanlatorFilter(mangaId, scanlators, include)
    }
}
