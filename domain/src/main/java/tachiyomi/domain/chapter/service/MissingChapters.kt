package tachiyomi.domain.chapter.service

import app.komikita.core.nativecore.NativeChapterEngine
import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.floor

fun List<Double>.missingChaptersCount(): Int {
    if (size >= 512) {
        NativeChapterEngine.missingChaptersCount(this)?.let { return it }
    }
    return missingChaptersCountKotlin()
}

fun List<Double>.missingChaptersCountKotlin(): Int {
    val chapters = IntArray(size)
    var count = 0
    for (number in this) {
        if (number.isFinite() && number >= 0.0 && number <= Int.MAX_VALUE.toDouble()) {
            chapters[count++] = number.toInt()
        }
    }
    java.util.Arrays.sort(chapters, 0, count)
    var previous = 0L
    var missing = 0L
    for (index in 0 until count) {
        val current = chapters[index].toLong()
        if (current > previous + 1) missing += current - previous - 1
        previous = current
    }
    return missing.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
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
