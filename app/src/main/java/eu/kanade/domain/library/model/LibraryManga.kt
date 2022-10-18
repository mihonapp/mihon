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
    val id: Long = manga.id

    val totalChapters = readCount + unreadCount

    val hasStarted = readCount > 0
}
