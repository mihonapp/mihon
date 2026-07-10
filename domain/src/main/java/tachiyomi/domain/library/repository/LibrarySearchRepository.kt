package tachiyomi.domain.library.repository

import tachiyomi.domain.library.model.search.QueryNode

interface LibrarySearchRepository {
    suspend fun getFilteredMangaIdsByAst(rootNode: QueryNode): Set<Long>
}
