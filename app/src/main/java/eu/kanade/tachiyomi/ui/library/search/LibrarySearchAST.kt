package eu.kanade.tachiyomi.ui.library.search

import tachiyomi.domain.library.repository.SqlQueryPart
import tachiyomi.source.local.LocalSource
import java.time.LocalDate
import java.time.ZoneId

enum class MangaField(vararg val aliases: String) {
    TITLE("title"),
    AUTHOR("author"),
    ARTIST("artist"),
    DESCRIPTION("description", "desc"),
    GENRE("genre", "tag"),
    SOURCE("source", "src"),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            field.aliases.map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): MangaField? = lookup[value.lowercase()]
    }
}

enum class ComparisonField(vararg val aliases: String) {
    ID("id"),
    DATE_ADDED("added"),
    FETCH_INTERVAL("fetchinterval", "fi"),
    NEXT_UPDATE("nextupdate", "nu"),
    UNREAD("unread"),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            field.aliases.map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): ComparisonField? = lookup[value.lowercase()]
    }
}

enum class Comparator(val symbol: String) {
    GTE(">="),
    LTE("<="),
    GT(">"),
    LT("<"),
    EQ("="),
    ;

    companion object {
        private val lookup = entries.associateBy { it.symbol }

        fun fromString(value: String): Comparator? = lookup[value]
    }
}

sealed interface QueryNode {
    fun toSqlQueryPart(): SqlQueryPart
}

data class AndNode(val children: List<QueryNode>) : QueryNode {
    override fun toSqlQueryPart(): SqlQueryPart {
        if (children.isEmpty()) return SqlQueryPart("1=1")

        val parts = children.map { it.toSqlQueryPart() }
        val combinedSql = parts.joinToString(" AND ") { "(${it.sql})" }
        val combinedArgs = parts.flatMap { it.args }

        return SqlQueryPart(combinedSql, combinedArgs)
    }
}

data class OrNode(val children: List<QueryNode>) : QueryNode {
    override fun toSqlQueryPart(): SqlQueryPart {
        if (children.isEmpty()) return SqlQueryPart("1=0")

        val parts = children.map { it.toSqlQueryPart() }
        val combinedSql = parts.joinToString(" OR ") { "(${it.sql})" }
        val combinedArgs = parts.flatMap { it.args }

        return SqlQueryPart(combinedSql, combinedArgs)
    }
}

data class NotNode(val child: QueryNode) : QueryNode {
    override fun toSqlQueryPart(): SqlQueryPart {
        val part = child.toSqlQueryPart()
        return SqlQueryPart("NOT (${part.sql})", part.args)
    }
}

object EmptyQueryNode : QueryNode {
    override fun toSqlQueryPart(): SqlQueryPart {
        return SqlQueryPart("1=1")
    }
}

data class GeneralQueryNode(val value: String, val negated: Boolean) : QueryNode {
    override fun toSqlQueryPart(): SqlQueryPart {
        var sql = """
            (
                instr(lower(libraryView.title), ?) > 0
                OR instr(lower(coalesce(libraryView.author, '')), ?) > 0
                OR instr(lower(coalesce(libraryView.artist, '')), ?) > 0
                OR instr(lower(coalesce(libraryView.description, '')), ?) > 0
                OR instr(lower(coalesce(libraryView.genre, '')), ?) > 0
                OR instr(lower(sources.name), ?) > 0
                ${if (value.equals("local", ignoreCase = true)) "OR libraryView.source = ${LocalSource.ID}" else ""}
            )
        """.trimIndent()
        val args = List(MangaField.entries.size) { value.lowercase() }
        if (negated) sql = "NOT $sql"

        return SqlQueryPart(sql, args)
    }
}

data class FieldQueryNode(val field: MangaField, val value: String, val negated: Boolean) : QueryNode {
    override fun toSqlQueryPart(): SqlQueryPart {
        if (field == MangaField.SOURCE && value.equals("local", ignoreCase = true)) {
            var sql = """
                (
                    instr(lower(sources.name), 'local') > 0
                    OR libraryView.source = ${LocalSource.ID}
                )
            """.trimIndent()
            if (negated) sql = "NOT $sql"
            return SqlQueryPart(sql)
        }

        val column = when (field) {
            MangaField.TITLE -> "lower(libraryView.title)"
            MangaField.AUTHOR -> "lower(coalesce(libraryView.author, ''))"
            MangaField.ARTIST -> "lower(coalesce(libraryView.artist, ''))"
            MangaField.DESCRIPTION -> "lower(coalesce(libraryView.description, ''))"
            MangaField.GENRE -> "lower(coalesce(libraryView.genre, ''))"
            MangaField.SOURCE -> "lower(sources.name)"
        }
        var sql = "instr($column, ?) > 0"
        if (negated) sql = "NOT $sql"
        return SqlQueryPart(sql, listOf(value.lowercase()))
    }
}

data class ComparisonQueryNode(
    val field: ComparisonField,
    val value: String,
    val queryComparator: Comparator,
    val negated: Boolean,
) : QueryNode {
    override fun toSqlQueryPart(): SqlQueryPart {
        val column = when (field) {
            ComparisonField.ID -> "libraryView._id"
            ComparisonField.DATE_ADDED -> "libraryView.date_added"
            ComparisonField.FETCH_INTERVAL -> "abs(libraryView.calculate_interval)"
            ComparisonField.NEXT_UPDATE -> "libraryView.next_update"
            ComparisonField.UNREAD -> "libraryView.totalCount - libraryView.readCount"
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

            ComparisonField.UNREAD -> value.toLongOrNull()
        } ?: return SqlQueryPart("1=0")

        var sql = "$column ${queryComparator.symbol} ?"
        if (negated) sql = "NOT $sql"
        return SqlQueryPart(sql, listOf(arg))
    }
}
