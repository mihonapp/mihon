package tachiyomi.data.library

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import tachiyomi.domain.library.model.search.AndNode
import tachiyomi.domain.library.model.search.ComparisonField
import tachiyomi.domain.library.model.search.ComparisonQueryNode
import tachiyomi.domain.library.model.search.EmptyQueryNode
import tachiyomi.domain.library.model.search.FieldQueryNode
import tachiyomi.domain.library.model.search.GeneralQueryNode
import tachiyomi.domain.library.model.search.MangaField
import tachiyomi.domain.library.model.search.NotNode
import tachiyomi.domain.library.model.search.OrNode
import tachiyomi.domain.library.model.search.QueryNode
import tachiyomi.domain.library.repository.LibrarySearchRepository
import java.time.LocalDate
import java.time.ZoneId

class LibrarySearchRepositoryImpl(
    private val driver: SqlDriver,
) : LibrarySearchRepository {
    override suspend fun getFilteredMangaIdsByAst(rootNode: QueryNode): Set<Long> {
        val queryPart = rootNode.toSqlQueryPart()

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

    private fun QueryNode.toSqlQueryPart(): SqlQueryPart = when (this) {
        is AndNode -> {
            if (children.isEmpty()) return SqlQueryPart("1=1")

            val parts = children.map { it.toSqlQueryPart() }
            val combinedSql = parts.joinToString(" AND ") { "(${it.sql})" }
            val combinedArgs = parts.flatMap { it.args }

            SqlQueryPart(combinedSql, combinedArgs)
        }

        is OrNode -> {
            if (children.isEmpty()) return SqlQueryPart("1=0")

            val parts = children.map { it.toSqlQueryPart() }
            val combinedSql = parts.joinToString(" OR ") { "(${it.sql})" }
            val combinedArgs = parts.flatMap { it.args }

            SqlQueryPart(combinedSql, combinedArgs)
        }

        is NotNode -> {
            val part = child.toSqlQueryPart()
            SqlQueryPart("NOT (${part.sql})", part.args)
        }

        is EmptyQueryNode -> return SqlQueryPart("1=1")

        is GeneralQueryNode -> toSqlQueryPart()

        is FieldQueryNode -> toSqlQueryPart()

        is ComparisonQueryNode -> toSqlQueryPart()
    }

    private fun GeneralQueryNode.toSqlQueryPart(): SqlQueryPart {
        val clauses = MangaField.entries.mapNotNull { field ->
            when (field) {
                MangaField.TITLE -> "libraryView.title"
                MangaField.AUTHOR -> "coalesce(libraryView.author, '')"
                MangaField.ARTIST -> "coalesce(libraryView.artist, '')"
                MangaField.DESCRIPTION -> "coalesce(libraryView.description, '')"
                MangaField.GENRE -> "coalesce(libraryView.genre, '')"
                MangaField.SOURCE -> "sources.name"
                MangaField.NOTES -> "libraryView.notes"

                MangaField.LANGUAGE -> null
            }
        }.map { "instr(lower($it), ?) > 0" }.toMutableList()
        if (value.equals("local", ignoreCase = true)) clauses.add("libraryView.source = $LOCAL_SOURCE_ID")

        var sql = clauses.joinToString(separator = " OR ", prefix = "(", postfix = ")")
        if (negated) sql = "NOT $sql"

        val args = List(MangaField.generalFieldCount) { value.lowercase() }

        return SqlQueryPart(sql, args)
    }

    private fun FieldQueryNode.toSqlQueryPart(): SqlQueryPart {
        if (field == MangaField.SOURCE && value.equals("local", ignoreCase = true)) {
            var sql = """
                (
                    instr(lower(sources.name), 'local') > 0
                    OR libraryView.source = $LOCAL_SOURCE_ID
                )
            """.trimIndent()
            if (negated) sql = "NOT $sql"
            return SqlQueryPart(sql)
        }

        val column = when (field) {
            MangaField.TITLE -> "libraryView.title"
            MangaField.AUTHOR -> "coalesce(libraryView.author, '')"
            MangaField.ARTIST -> "coalesce(libraryView.artist, '')"
            MangaField.DESCRIPTION -> "coalesce(libraryView.description, '')"
            MangaField.GENRE -> "coalesce(libraryView.genre, '')"
            MangaField.SOURCE -> "sources.name"
            MangaField.NOTES -> "libraryView.notes"
            MangaField.LANGUAGE -> "sources.lang"
        }

        if (value.isEmpty()) {
            var sql = "$column = ''"
            if (negated) sql = "NOT $sql"
            return SqlQueryPart(sql)
        }

        var sql = "instr(lower($column), ?) > 0"
        if (negated) sql = "NOT $sql"
        return SqlQueryPart(sql, listOf(value.lowercase()))
    }

    private fun ComparisonQueryNode.toSqlQueryPart(): SqlQueryPart {
        val column = when (field) {
            ComparisonField.ID -> "libraryView._id"
            ComparisonField.DATE_ADDED -> "libraryView.date_added"
            ComparisonField.FETCH_INTERVAL -> "abs(libraryView.calculate_interval)"
            ComparisonField.NEXT_UPDATE -> "libraryView.next_update"
            ComparisonField.UNREAD -> "libraryView.totalCount - libraryView.readCount"
            ComparisonField.READ -> "libraryView.readCount"
            ComparisonField.TOTAL -> "libraryView.totalCount"
        }

        val arg = when (field) {
            ComparisonField.ID -> value.toLongOrNull()

            ComparisonField.DATE_ADDED, ComparisonField.NEXT_UPDATE -> {
                runCatching {
                    LocalDate.parse(value)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()
            }

            ComparisonField.FETCH_INTERVAL -> value.toIntOrNull()

            ComparisonField.UNREAD, ComparisonField.READ, ComparisonField.TOTAL -> value.toLongOrNull()
        } ?: return SqlQueryPart("1=0")

        var sql = "$column ${queryComparator.symbol} ?"
        if (negated) sql = "NOT $sql"
        return SqlQueryPart(sql, listOf(arg))
    }
}

data class SqlQueryPart(val sql: String, val args: List<Any> = emptyList())

private const val LOCAL_SOURCE_ID = 0L
