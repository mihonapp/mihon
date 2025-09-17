package tachiyomi.domain.library.model

import tachiyomi.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val categories: List<Long>,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
) {
    val id: Long = manga.id

    val unreadCount
        get() = totalChapters - readCount

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasStarted = readCount > 0
}
