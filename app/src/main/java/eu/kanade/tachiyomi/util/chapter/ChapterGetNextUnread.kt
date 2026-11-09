package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.manga.model.Manga

/**
 * Gets the chapter to continue reading from, with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(manga: Manga, downloadManager: DownloadManager): Chapter? {
    return findNextToRead(manga, applyFilters(manga, downloadManager))
}

/**
 * Gets the chapter to continue reading from, with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
    return map { it.chapter }.findNextToRead(manga, applyFilters(manga).map { it.chapter }.toList())
}

/**
 * Continues from the furthest chapter that was read or started: that chapter if it's unfinished, otherwise the first
 * unread one after it. Without any progress this is the first unread chapter. Progress is judged on all chapters, so
 * a filter hiding read chapters doesn't lose the reading position.
 */
internal fun List<Chapter>.findNextToRead(manga: Manga, visibleChapters: List<Chapter>): Chapter? {
    val readingOrder = Comparator(getChapterSort(manga, sortDescending = false))
    val unread = visibleChapters.filterNot { it.read }.sortedWith(readingOrder)
    val furthest = filter { it.read || it.lastPageRead > 0 }.maxWithOrNull(readingOrder)
        ?: return unread.firstOrNull()
    return unread.firstOrNull { readingOrder.compare(it, furthest) >= 0 } ?: unread.firstOrNull()
}
