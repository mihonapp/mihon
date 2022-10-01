package eu.kanade.tachiyomi.ui.library

import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryItem(
    val libraryManga: LibraryManga,
    private val sourceManager: SourceManager = Injekt.get(),
) {

    var displayMode: Long = -1
    var downloadCount: Long = -1
    var unreadCount: Long = -1
    var isLocal = false
    var sourceLanguage = ""

    /**
     * Filters a manga depending on a query.
     *
     * @param constraint the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    fun filter(constraint: String): Boolean {
        val sourceName by lazy { sourceManager.getOrStub(libraryManga.manga.source).getNameForMangaInfo() }
        val genres by lazy { libraryManga.manga.genre }
        return libraryManga.manga.title.contains(constraint, true) ||
            (libraryManga.manga.author?.contains(constraint, true) ?: false) ||
            (libraryManga.manga.artist?.contains(constraint, true) ?: false) ||
            (libraryManga.manga.description?.contains(constraint, true) ?: false) ||
            if (constraint.contains(",")) {
                constraint.split(",").all { containsSourceOrGenre(it.trim(), sourceName, genres) }
            } else {
                containsSourceOrGenre(constraint, sourceName, genres)
            }
    }

    /**
     * Filters a manga by checking whether the query is the manga's source OR part of
     * the genres of the manga
     * Checking for genre is done only if the query isn't part of the source name.
     *
     * @param query the query to check
     * @param sourceName name of the manga's source
     * @param genres list containing manga's genres
     */
    private fun containsSourceOrGenre(query: String, sourceName: String, genres: List<String>?): Boolean {
        val minus = query.startsWith("-")
        val tag = if (minus) { query.substringAfter("-") } else query
        return when (sourceName.contains(tag, true)) {
            false -> containsGenre(query, genres)
            else -> !minus
        }
    }

    private fun containsGenre(tag: String, genres: List<String>?): Boolean {
        return if (tag.startsWith("-")) {
            genres?.find {
                it.trim().equals(tag.substringAfter("-"), ignoreCase = true)
            } == null
        } else {
            genres?.find {
                it.trim().equals(tag, ignoreCase = true)
            } != null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LibraryItem

        if (libraryManga != other.libraryManga) return false
        if (sourceManager != other.sourceManager) return false
        if (displayMode != other.displayMode) return false
        if (downloadCount != other.downloadCount) return false
        if (unreadCount != other.unreadCount) return false
        if (isLocal != other.isLocal) return false
        if (sourceLanguage != other.sourceLanguage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = libraryManga.hashCode()
        result = 31 * result + sourceManager.hashCode()
        result = 31 * result + displayMode.hashCode()
        result = 31 * result + downloadCount.toInt()
        result = 31 * result + unreadCount.toInt()
        result = 31 * result + isLocal.hashCode()
        result = 31 * result + sourceLanguage.hashCode()
        return result
    }
}
