package tachiyomi.domain.library.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy

/**
 * Lightweight library manga model for update checks.
 * Contains only the fields needed for filtering which manga to update,
 * avoiding expensive fields like description, genre, etc.
 */
data class LibraryMangaForUpdate(
    val id: Long,
    val source: Long,
    val url: String,
    val title: String,
    val status: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val updateStrategy: UpdateStrategy,
    val totalChapters: Long,
    val readCount: Long,
    val categories: List<Long>,
) {
    val unreadCount: Long
        get() = totalChapters - readCount

    val hasStarted: Boolean
        get() = readCount > 0

    // Extension function to convert to Manga model for legacy compatibility
    // Note: Fields like description, artist, author will be missing/null
    fun toManga(): tachiyomi.domain.manga.model.Manga {
        return tachiyomi.domain.manga.model.Manga(
            id = id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate,
            nextUpdate = nextUpdate,
            fetchInterval = 0, // Not available in this model
            dateAdded = 0,
            viewerFlags = 0,
            chapterFlags = 0,
            coverLastModified = 0,
            url = url,
            title = title,
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = status,
            thumbnailUrl = null,
            updateStrategy = updateStrategy,
            initialized = false,
            lastModifiedAt = 0,
            favoriteModifiedAt = null,
            version = 0,
            notes = "",
            alternativeTitles = emptyList(),
        )
    }

    // Convert to LibraryManga for compatibility
    fun toLibraryManga(): LibraryManga {
        return LibraryManga(
            manga = toManga(),
            categories = categories,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
        )
    }
}
