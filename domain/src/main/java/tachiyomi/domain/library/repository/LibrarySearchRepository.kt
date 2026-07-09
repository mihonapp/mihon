package tachiyomi.domain.library.repository

interface LibrarySearchRepository {
    suspend fun getFilteredMangaIdsByQuery(queryPart: SqlQueryPart): Set<Long>
}

data class SqlQueryPart(val sql: String, val args: List<Any> = emptyList())
