package eu.kanade.domain.library.model

import eu.kanade.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val category: Long,
    val unreadCount: Long,
    val readCount: Long,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
) {
    val totalChapters
        get() = readCount + unreadCount

    val hasStarted
        get() = readCount > 0
}
