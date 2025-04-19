package mihon.domain.manga.local.interactor

import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.local.repository.LocalMangaRepository

class GetLocalSourceMangaOrderedByTitleAsc(
    private val localMangaRepository: LocalMangaRepository,
) {

    suspend fun await(urls: List<String>): List<SManga> {
        return localMangaRepository.getSMangaOrderedByTitleAsc(urls)
    }
}
