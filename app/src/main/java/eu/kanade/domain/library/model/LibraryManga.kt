package eu.kanade.domain.library.model

import eu.kanade.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val category: Long,
    val unreadCount: Long,
    val readCount: Long,
) {
    val totalChapters
        get() = readCount + unreadCount

    val hasStarted
        get() = readCount > 0
}
