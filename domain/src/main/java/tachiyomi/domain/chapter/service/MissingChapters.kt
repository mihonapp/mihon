package tachiyomi.domain.chapter.service

fun List<Float>.missingChaptersCount(): Int {
    if (this.isEmpty()) {
        return 0
    }

    val chapters = this
        // Ignore unknown chapter numbers
        .filterNot { it == -1f }
        // Convert to integers, as we cannot check if 16.5 is missing
        .map(Float::toInt)
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
