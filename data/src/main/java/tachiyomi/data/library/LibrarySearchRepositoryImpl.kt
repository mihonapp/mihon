package tachiyomi.data.library

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import tachiyomi.domain.library.repository.LibrarySearchRepository
import tachiyomi.domain.library.repository.SqlQueryPart

class LibrarySearchRepositoryImpl(
    private val driver: SqlDriver,
) : LibrarySearchRepository {
    override suspend fun getFilteredMangaIdsByQuery(queryPart: SqlQueryPart): Set<Long> {
        return driver.executeQuery(
            identifier = null,
            sql = """
                SELECT libraryView._id
                FROM libraryView
                LEFT JOIN sources on libraryView.source = sources._id
                WHERE ${queryPart.sql}
            """.trimIndent(),
            mapper = { cursor ->
                val result = buildSet {
                    while (cursor.next().value) add(cursor.getLong(0)!!)
                }
                QueryResult.Value(result)
            },
            parameters = queryPart.args.size,
        ) {
            queryPart.args.forEachIndexed { index, arg ->
                when (arg) {
                    is Long -> bindLong(index, arg)
                    is Int -> bindLong(index, arg.toLong())
                    is String -> bindString(index, arg)
                }
            }
        }.await()
    }
}
