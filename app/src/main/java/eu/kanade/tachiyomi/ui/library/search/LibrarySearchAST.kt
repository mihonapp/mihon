package eu.kanade.tachiyomi.ui.library.search

import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.source.local.LocalSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

enum class MangaField(vararg val aliases: String) {
    ID("id"),
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
    GTE(">=") {
        override fun <T : Comparable<T>> apply(a: T, b: T) = a >= b
    },
    LTE("<=") {
        override fun <T : Comparable<T>> apply(a: T, b: T) = a <= b
    },
    GT(">") {
        override fun <T : Comparable<T>> apply(a: T, b: T) = a > b
    },
    LT("<") {
        override fun <T : Comparable<T>> apply(a: T, b: T) = a < b
    },
    EQ("=") {
        override fun <T : Comparable<T>> apply(a: T, b: T) = a == b
    }, ;

    abstract fun <T : Comparable<T>> apply(a: T, b: T): Boolean

    companion object {
        private val lookup = entries.associateBy { it.symbol }

        fun fromString(value: String): Comparator? = lookup[value]
    }
}

sealed interface QueryNode {
    fun matches(item: LibraryItem): Boolean
}

data class AndNode(val children: List<QueryNode>) : QueryNode {
    override fun matches(item: LibraryItem): Boolean {
        return children.all { it.matches(item) }
    }
}

data class OrNode(val children: List<QueryNode>) : QueryNode {
    override fun matches(item: LibraryItem): Boolean {
        return children.any { it.matches(item) }
    }
}

data class NotNode(val child: QueryNode) : QueryNode {
    override fun matches(item: LibraryItem): Boolean {
        return !child.matches(item)
    }
}

object EmptyQueryNode : QueryNode {
    override fun matches(item: LibraryItem): Boolean = true
}

data class GeneralQueryNode(val value: String, val negated: Boolean) : QueryNode {
    override fun matches(item: LibraryItem): Boolean {
        val manga = item.libraryManga.manga

        val match = manga.title.contains(value, ignoreCase = true) ||
            manga.author?.contains(value, ignoreCase = true) ?: false ||
            manga.artist?.contains(value, ignoreCase = true) ?: false ||
            manga.description?.contains(value, ignoreCase = true) ?: false ||
            manga.genre?.any { it.contains(value, ignoreCase = true) } ?: false ||
            item.sourceName.contains(value, ignoreCase = true) ||
            (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)

        return if (negated) !match else match
    }
}

data class FieldQueryNode(val field: MangaField, val value: String, val negated: Boolean) : QueryNode {
    override fun matches(item: LibraryItem): Boolean {
        val manga = item.libraryManga.manga

        val match = when (field) {
            MangaField.ID -> item.id == value.toLongOrNull()
            MangaField.TITLE -> manga.title.contains(value, ignoreCase = true)
            MangaField.AUTHOR -> manga.author?.contains(value, ignoreCase = true) ?: false
            MangaField.ARTIST -> manga.artist?.contains(value, ignoreCase = true) ?: false
            MangaField.DESCRIPTION -> manga.description?.contains(value, ignoreCase = true) ?: false
            MangaField.GENRE -> manga.genre?.any { it.equals(value, ignoreCase = true) } ?: false
            MangaField.SOURCE -> {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }
        }
        return if (negated) !match else match
    }
}

data class ComparisonQueryNode(
    val field: ComparisonField,
    val value: String,
    val queryComparator: Comparator,
    val negated: Boolean,
) : QueryNode {
    override fun matches(item: LibraryItem): Boolean {
        val manga = item.libraryManga.manga

        val match = when (field) {
            ComparisonField.DATE_ADDED -> {
                runCatching { LocalDate.parse(value) }.getOrNull()?.let { inputDate ->
                    queryComparator.apply(
                        Instant.ofEpochMilli(manga.dateAdded).atZone(ZoneId.systemDefault()).toLocalDate(),
                        inputDate,
                    )
                } ?: false
            }

            ComparisonField.FETCH_INTERVAL -> {
                value.toIntOrNull()?.let { inputValue ->
                    queryComparator.apply(abs(manga.fetchInterval), inputValue)
                } ?: false
            }

            ComparisonField.NEXT_UPDATE -> {
                runCatching { LocalDate.parse(value) }.getOrNull()?.let { inputDate ->
                    queryComparator.apply(
                        Instant.ofEpochMilli(manga.nextUpdate).atZone(ZoneId.systemDefault()).toLocalDate(),
                        inputDate,
                    )
                } ?: false
            }

            ComparisonField.UNREAD -> {
                value.toLongOrNull()?.let { inputUnread ->
                    queryComparator.apply(item.unreadCount, inputUnread)
                } ?: false
            }
        }

        return if (negated) !match else match
    }
}
