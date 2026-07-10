package tachiyomi.domain.library.model.search

enum class MangaField(vararg val aliases: String) {
    TITLE("title"),
    AUTHOR("author"),
    ARTIST("artist"),
    DESCRIPTION("description", "desc"),
    GENRE("genre", "tag"),
    SOURCE("source", "src"),
    NOTES("notes", "note"),
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
    READ("read"),
    TOTAL("total"),
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

sealed interface QueryNode

data class AndNode(val children: List<QueryNode>) : QueryNode

data class OrNode(val children: List<QueryNode>) : QueryNode

data class NotNode(val child: QueryNode) : QueryNode

object EmptyQueryNode : QueryNode

data class GeneralQueryNode(val value: String, val negated: Boolean) : QueryNode

data class FieldQueryNode(val field: MangaField, val value: String, val negated: Boolean) : QueryNode

data class ComparisonQueryNode(
    val field: ComparisonField,
    val value: String,
    val queryComparator: Comparator,
    val negated: Boolean,
) : QueryNode
