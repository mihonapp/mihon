package mihon.domain.manga.local.interactor

import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.local.repository.LocalMangaRepository

class GetLocalSourceMangaByUrl(
    private val localMangaRepository: LocalMangaRepository,
) {

    suspend fun await(url: String): SManga? {
        return localMangaRepository.getSMangaByUrl(url)
    }
}
