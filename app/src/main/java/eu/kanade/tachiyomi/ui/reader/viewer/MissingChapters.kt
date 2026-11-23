package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import tachiyomi.domain.chapter.service.calculateChapterGap as domainCalculateChapterGap

fun calculateChapterGap(higherReaderChapter: ReaderChapter?, lowerReaderChapter: ReaderChapter?): Int {
    val gap = domainCalculateChapterGap(
        higherReaderChapter?.chapter,
        lowerReaderChapter?.chapter,
    )
    if (gap <= 0 || higherReaderChapter == null || lowerReaderChapter == null) return gap

    val lowerNumber = lowerReaderChapter.chapter.chapterNumber.toInt()
    val higherNumber = higherReaderChapter.chapter.chapterNumber.toInt()
    val hiddenNumbers = higherReaderChapter.hiddenChapterNumbers + lowerReaderChapter.hiddenChapterNumbers
    return (gap - hiddenNumbers.count { it > lowerNumber && it < higherNumber }).coerceAtLeast(0)
}
