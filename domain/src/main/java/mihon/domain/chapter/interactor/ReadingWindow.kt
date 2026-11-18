package mihon.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter

/**
 * Chapters that may be auto-downloaded: the next [amount] unread chapters after the furthest
 * chapter the user has read or started.
 *
 * A chapter counts as started when it is marked read, has page progress, or was opened
 * (present in [openedChapterIds]). The anchor itself is not part of the window, so a chapter
 * the user is currently on is treated the same as a finished one.
 *
 * When nothing has been started, the window is the first [amount] unread chapters.
 */
fun readingWindowChapterIds(
    chaptersInReadingOrder: List<Chapter>,
    openedChapterIds: Set<Long>,
    amount: Int,
): Set<Long> {
    if (amount <= 0 || chaptersInReadingOrder.isEmpty()) return emptySet()

    val anchorIndex = chaptersInReadingOrder.indexOfLast { chapter ->
        chapter.read || chapter.lastPageRead > 0 || chapter.id in openedChapterIds
    }
    val afterAnchor = if (anchorIndex < 0) {
        chaptersInReadingOrder
    } else {
        chaptersInReadingOrder.drop(anchorIndex + 1)
    }

    return afterAnchor
        .filterNot { it.read }
        .take(amount)
        .map { it.id }
        .toSet()
}
