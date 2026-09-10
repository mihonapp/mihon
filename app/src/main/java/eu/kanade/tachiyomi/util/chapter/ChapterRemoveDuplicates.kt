package eu.kanade.tachiyomi.util.chapter

import tachiyomi.domain.chapter.model.Chapter

/**
 * Returns a copy of the list with duplicate chapters removed
 *
 * Due to some sources naming their chapters with seasons and giving multiple different chapters the same chapter number
 * as mentioned in ( https://github.com/mihonapp/mihon/issues/3623#issue-4939611370 )
 * The duplicated are removed if they are adjacent to each other in the chapter list
 * An ideal solution would be to split the chapters into a unique list for each season, but that relies on how chapters are named which isn't standardised
 */

fun List<Chapter>.removeDuplicates(currentChapter: Chapter): List<Chapter> {
    fun Chapter.priority() = when {
        id == currentChapter.id -> 2
        scanlator == currentChapter.scanlator -> 1
        else -> 0
    }

    return fold(mutableListOf()) { acc, chapter ->
        val last = acc.lastOrNull()
        when {
            last == null || last.chapterNumber != chapter.chapterNumber -> acc.add(chapter)
            chapter.priority() > last.priority() -> acc[acc.lastIndex] = chapter
            else -> Unit
        }
        acc
    }
}
