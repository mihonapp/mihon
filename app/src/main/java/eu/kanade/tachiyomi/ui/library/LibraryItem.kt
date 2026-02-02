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
     * Gets the full URL by combining the source base URL with the manga URL path
     */
    val fullUrl: String by lazy {
        val source = sourceManager.getOrStub(libraryManga.manga.source)
        val baseUrl = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl ?: ""
        val mangaUrl = libraryManga.manga.url
        if (baseUrl.isNotEmpty() && mangaUrl.isNotEmpty()) {
            if (mangaUrl.startsWith("http://") || mangaUrl.startsWith("https://")) {
                mangaUrl // Already a full URL
            } else {
                baseUrl.trimEnd('/') + (if (mangaUrl.startsWith("/")) mangaUrl else "/$mangaUrl")
            }
        } else {
            mangaUrl
        }
    }

    /**
     * Checks if a query matches the manga
     *
     * @param constraint the query to check.
     * @param chapterMatchIds optional set of manga IDs that have chapters matching the query.
     *                        When provided, manga with matching chapters are included in results.
     * @param searchByUrl whether to include URL in search.
     * @param useRegex whether to use regex matching.
     * @return true if the manga matches the query, false otherwise.
     */
    fun matches(
        constraint: String,
        chapterMatchIds: Set<Long> = emptySet(),
        searchByUrl: Boolean = false,
        useRegex: Boolean = false,
    ): Boolean {
        val sourceName by lazy { sourceManager.getOrStub(libraryManga.manga.source).getNameForMangaInfo() }
        
        // Special prefixes for searching specific fields
        if (constraint.startsWith("id:", true)) {
            return id == constraint.substringAfter("id:").toLongOrNull()
        }
        if (constraint.startsWith("title:", true)) {
            val query = constraint.substringAfter("title:").trim()
            return matchString(libraryManga.manga.title, query, useRegex)
        }
        if (constraint.startsWith("author:", true)) {
            val query = constraint.substringAfter("author:").trim()
            return libraryManga.manga.author?.let { matchString(it, query, useRegex) } ?: false
        }
        if (constraint.startsWith("artist:", true)) {
            val query = constraint.substringAfter("artist:").trim()
            return libraryManga.manga.artist?.let { matchString(it, query, useRegex) } ?: false
        }
        if (constraint.startsWith("desc:", true) || constraint.startsWith("description:", true)) {
            val query = if (constraint.startsWith("desc:")) {
                constraint.substringAfter("desc:").trim()
            } else {
                constraint.substringAfter("description:").trim()
            }
            return libraryManga.manga.description?.let { matchString(it, query, useRegex) } ?: false
        }
        if (constraint.startsWith("tag:", true) || constraint.startsWith("genre:", true)) {
            val query = if (constraint.startsWith("tag:")) {
                constraint.substringAfter("tag:").trim()
            } else {
                constraint.substringAfter("genre:").trim()
            }
            return libraryManga.manga.genre?.any { matchString(it, query, useRegex) } ?: false
        }
        if (constraint.startsWith("source:", true)) {
            val query = constraint.substringAfter("source:").trim()
            return matchString(sourceName, query, useRegex)
        }
        if (constraint.startsWith("url:", true)) {
            val query = constraint.substringAfter("url:").trim()
            return matchString(libraryManga.manga.url, query, useRegex)
        }
        // Search for chapter names explicitly
        if (constraint.startsWith("chapter:", true)) {
            return chapterMatchIds.contains(id)
        }
        
        // Default: search title, author, artist, description, source, tags, URL (if enabled), and optionally chapters
        val basicMatch = matchString(libraryManga.manga.title, constraint, useRegex) ||
            (libraryManga.manga.author?.let { matchString(it, constraint, useRegex) } ?: false) ||
            (libraryManga.manga.artist?.let { matchString(it, constraint, useRegex) } ?: false) ||
            (libraryManga.manga.description?.let { matchString(it, constraint, useRegex) } ?: false) ||
            (searchByUrl && matchString(libraryManga.manga.url, constraint, useRegex)) ||
            constraint.split(",").map { it.trim() }.all { subconstraint ->
                checkNegatableConstraint(subconstraint) {
                    matchString(sourceName, it, useRegex) ||
                        (libraryManga.manga.genre?.any { genre -> matchString(genre, it, useRegex) } ?: false)
                }
            }
        
        // Include chapter name matches if available
        return basicMatch || chapterMatchIds.contains(id)
    }

    /**
     * Match a string against a query, optionally using regex.
     */
    private fun matchString(text: String, query: String, useRegex: Boolean): Boolean {
        return if (useRegex) {
            try {
                Regex(query, RegexOption.IGNORE_CASE).containsMatchIn(text)
            } catch (e: Exception) {
                // Invalid regex, fall back to simple contains
                text.contains(query, ignoreCase = true)
            }
        } else {
            text.contains(query, ignoreCase = true)
        }
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
