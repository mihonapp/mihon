package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder

fun getChapterSort(manga: Manga, sortDescending: Boolean = manga.sortDescending()): (Chapter, Chapter) -> Int {
    return when (manga.sorting) {
        Manga.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
            false -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
        }
        Manga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.chapter_number.toString().compareToCaseInsensitiveNaturalOrder(c1.chapter_number.toString()) }
            false -> { c1, c2 -> c1.chapter_number.toString().compareToCaseInsensitiveNaturalOrder(c2.chapter_number.toString()) }
        }
        Manga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.date_upload.compareTo(c1.date_upload) }
            false -> { c1, c2 -> c1.date_upload.compareTo(c2.date_upload) }
        }
        else -> throw NotImplementedError("Unimplemented sorting method")
    }
}
