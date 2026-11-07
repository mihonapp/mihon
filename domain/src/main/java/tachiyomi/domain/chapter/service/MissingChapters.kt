package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.floor

fun List<Double>.missingChaptersCount(): Int {
    if (this.isEmpty()) {
        return 0
    }

    val chapters = this
        // Ignore unknown chapter numbers
        .filterNot { it == -1.0 }
        // Convert to integers, as we cannot check if 16.5 is missing
        .map(Double::toInt)
        // Only keep unique chapters so that -1 or 16 are not counted multiple times
        .distinct()
        .sorted()

    return chapters.asIterable().missingChaptersCount()
}

/** Counts gaps in chapter numbers that have already been normalized, deduplicated and sorted. */
fun Iterable<Int>.missingChaptersCount(): Int {
    var missingChaptersCount = 0
    var previousChapter = 0

    for (currentChapter in this) {
        if (currentChapter > previousChapter + 1) {
            missingChaptersCount += currentChapter - previousChapter - 1
        }
        previousChapter = currentChapter
    }

    return missingChaptersCount
}

fun calculateChapterGap(higherChapter: Chapter?, lowerChapter: Chapter?): Int {
    if (higherChapter == null || lowerChapter == null) return 0
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return 0
    return calculateChapterGap(higherChapter.chapterNumber, lowerChapter.chapterNumber)
}

fun calculateChapterGap(higherChapterNumber: Double, lowerChapterNumber: Double): Int {
    if (higherChapterNumber < 0.0 || lowerChapterNumber < 0.0) return 0
    return floor(higherChapterNumber).toInt() - floor(lowerChapterNumber).toInt() - 1
}
