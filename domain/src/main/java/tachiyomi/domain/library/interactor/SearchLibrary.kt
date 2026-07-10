package tachiyomi.domain.library.interactor

import tachiyomi.domain.library.model.search.LibrarySearchLexer
import tachiyomi.domain.library.model.search.LibrarySearchParser
import tachiyomi.domain.library.repository.LibrarySearchRepository

class SearchLibrary(
    private val librarySearchRepository: LibrarySearchRepository,
) {
    suspend fun await(searchQuery: String): Set<Long> {
        val tokens = LibrarySearchLexer.tokenize(searchQuery)
        val rootNode = LibrarySearchParser(tokens).parse()

        return librarySearchRepository.getFilteredMangaIdsByAst(rootNode)
    }
}
