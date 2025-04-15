package mihon.domain.manga.local.interactor

import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.local.repository.LocalMangaRepository

class GetAllLocalSourceMangaOrderedByTitleDesc(
    private val localMangaRepository: LocalMangaRepository,
) {

    suspend fun await(): List<SManga> {
        return localMangaRepository.getAllSMangaOrderedByTitleDesc()
    }
}
