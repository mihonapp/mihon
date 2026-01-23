package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.repository.ChapterRepository

/**
 * Interactor to find manga IDs that have chapters matching a search query.
 * Used for extended library search functionality.
 */
class SearchChapterNames(
    private val chapterRepository: ChapterRepository,
) {
    /**
     * Find manga IDs where any chapter name contains the query.
     *
     * @param query The search query to match against chapter names.
     * @return List of manga IDs that have at least one chapter with a name containing the query.
     */
    suspend fun await(query: String): List<Long> {
        if (query.isBlank()) return emptyList()
        return chapterRepository.findMangaIdsWithChapterNameMatching(query)
    }
}
