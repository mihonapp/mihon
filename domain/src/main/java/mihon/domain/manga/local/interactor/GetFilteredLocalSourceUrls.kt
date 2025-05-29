package mihon.domain.manga.local.interactor

import mihon.domain.manga.local.repository.LocalMangaRepository

class GetFilteredLocalSourceUrls(
    private val localMangaRepository: LocalMangaRepository,
) {

    suspend fun await(
        excludedAuthors: Collection<String>,
        excludedArtists: Collection<String>,
        excludedGenres: Collection<String>,
        excludedStatuses: Collection<Long>,
        includedAuthors: Collection<String>,
        includedArtists: Collection<String>,
        includedGenres: Collection<String>,
        includedStatuses: Collection<Long>,
    ): List<String> {
        return localMangaRepository.getFilteredLocalSourceUrls(
            excludedAuthors = excludedAuthors,
            excludedArtists = excludedArtists,
            excludedGenres = excludedGenres,
            excludedStatuses = excludedStatuses,
            includedAuthors = includedAuthors,
            includedArtists = includedArtists,
            includedGenres = includedGenres,
            includedStatuses = includedStatuses,
        )
    }
}
