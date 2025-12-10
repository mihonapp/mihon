package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.floor

fun List<Double>.missingChaptersCount(): Int {
    if (this.isEmpty()) {
        return 0
    }

    // Count how many chapters have unknown/unrecognized numbers
    val unknownCount = this.count { it == -1.0 }
    val totalCount = this.size
    
    // If more than half of the chapters have unknown numbers, don't show missing chapters warning
    // This is common for novel sources where chapter numbers can't be parsed from titles
    if (unknownCount > totalCount / 2) {
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

    if (chapters.isEmpty()) {
        return 0
    }

    var missingChaptersCount = 0
    var previousChapter = 0 // The actual chapter number, not the array index

    // We go from 0 to lastChapter - Make sure to use the current index instead of the value
    for (i in chapters.indices) {
        val currentChapter = chapters[i]
        if (currentChapter > previousChapter + 1) {
            // Add the amount of missing chapters
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
