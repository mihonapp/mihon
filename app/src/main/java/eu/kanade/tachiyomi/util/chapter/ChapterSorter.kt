package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga

fun getChapterSort(manga: Manga, sortDescending: Boolean = manga.sortDescending()): (Chapter, Chapter) -> Int {
    return when (manga.sorting) {
        Manga.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }
        Manga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.chapterNumber.compareTo(c1.chapterNumber) }
            false -> { c1, c2 -> c1.chapterNumber.compareTo(c2.chapterNumber) }
        }
        Manga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.dateUpload.compareTo(c1.dateUpload) }
            false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
        }
        else -> throw NotImplementedError("Invalid chapter sorting method: ${manga.sorting}")
    }
}
