package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.source.getNameForMangaInfo
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class LibraryItem(
    val libraryManga: LibraryManga,
    val downloadCount: Long = -1,
    val unreadCount: Long = -1,
    val isLocal: Boolean = false,
    val sourceLanguage: String = "",
    private val sourceManager: SourceManager = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
) {
    val id: Long = libraryManga.id

    /**
     * Checks if a query matches the manga
     *
     * @param constraint the query to check.
     * @param chapterMatchIds optional set of manga IDs that have chapters matching the query.
     *                        When provided, manga with matching chapters are included in results.
     * @return true if the manga matches the query, false otherwise.
     */
    fun matches(constraint: String, chapterMatchIds: Set<Long> = emptySet()): Boolean {
        val sourceName by lazy { sourceManager.getOrStub(libraryManga.manga.source).getNameForMangaInfo() }
        
        // Special prefixes for searching specific fields
        if (constraint.startsWith("id:", true)) {
            return id == constraint.substringAfter("id:").toLongOrNull()
        }
        if (constraint.startsWith("title:", true)) {
            return libraryManga.manga.title.contains(constraint.substringAfter("title:").trim(), true)
        }
        if (constraint.startsWith("author:", true)) {
            return libraryManga.manga.author?.contains(constraint.substringAfter("author:").trim(), true) ?: false
        }
        if (constraint.startsWith("artist:", true)) {
            return libraryManga.manga.artist?.contains(constraint.substringAfter("artist:").trim(), true) ?: false
        }
        if (constraint.startsWith("desc:", true) || constraint.startsWith("description:", true)) {
            val query = if (constraint.startsWith("desc:")) {
                constraint.substringAfter("desc:").trim()
            } else {
                constraint.substringAfter("description:").trim()
            }
            return libraryManga.manga.description?.contains(query, true) ?: false
        }
        if (constraint.startsWith("tag:", true) || constraint.startsWith("genre:", true)) {
            val query = if (constraint.startsWith("tag:")) {
                constraint.substringAfter("tag:").trim()
            } else {
                constraint.substringAfter("genre:").trim()
            }
            return libraryManga.manga.genre?.any { it.contains(query, true) } ?: false
        }
        if (constraint.startsWith("source:", true)) {
            return sourceName.contains(constraint.substringAfter("source:").trim(), true)
        }
        // Search for chapter names explicitly
        if (constraint.startsWith("chapter:", true)) {
            return chapterMatchIds.contains(id)
        }
        
        // Default: search title, author, artist, description, source, tags, and optionally chapters
        val basicMatch = libraryManga.manga.title.contains(constraint, true) ||
            (libraryManga.manga.author?.contains(constraint, true) ?: false) ||
            (libraryManga.manga.artist?.contains(constraint, true) ?: false) ||
            (libraryManga.manga.description?.contains(constraint, true) ?: false) ||
            constraint.split(",").map { it.trim() }.all { subconstraint ->
                checkNegatableConstraint(subconstraint) {
                    sourceName.contains(it, true) ||
                        (libraryManga.manga.genre?.any { genre -> genre.equals(it, true) } ?: false)
                }
            }
        
        // Include chapter name matches if available
        return basicMatch || chapterMatchIds.contains(id)
    }

    /**
     * Checks a predicate on a negatable constraint. If the constraint starts with a minus character,
     * the minus is stripped and the result of the predicate is inverted.
     *
     * @param constraint the argument to the predicate. Inverts the predicate if it starts with '-'.
     * @param predicate the check to be run against the constraint.
     * @return !predicate(x) if constraint = "-x", otherwise predicate(constraint)
     */
    private fun checkNegatableConstraint(
        constraint: String,
        predicate: (String) -> Boolean,
    ): Boolean {
        return if (constraint.startsWith("-")) {
            !predicate(constraint.substringAfter("-").trimStart())
        } else {
            predicate(constraint)
        }
    }
}
