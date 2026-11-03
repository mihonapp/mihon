package tachiyomi.domain.library.model

import tachiyomi.domain.manga.model.Manga
import kotlin.time.Instant

data class LibraryManga(
    val manga: Manga,
    val categories: List<Long>,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Instant?,
    val chapterFetchedAt: Instant?,
    val lastRead: Instant?,
) {
    val id: Long = manga.id

    val unreadCount
        get() = totalChapters - readCount

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasStarted = readCount > 0
}
