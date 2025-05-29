package mihon.domain.manga.local.interactor

import mihon.domain.manga.local.repository.LocalMangaRepository

class GetLocalSourceFilterValues(
    private val localMangaRepository: LocalMangaRepository,
) {

    suspend fun await(): Triple<List<String>, List<String>, List<String>> {
        return localMangaRepository.getLocalSourceFilterValues()
    }
}
