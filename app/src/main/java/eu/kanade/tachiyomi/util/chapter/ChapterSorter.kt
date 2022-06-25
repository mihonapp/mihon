package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.domain.chapter.model.Chapter as DomainChapter
import eu.kanade.domain.manga.model.Manga as DomainManga

fun getChapterSort(manga: Manga, sortDescending: Boolean = manga.sortDescending()): (Chapter, Chapter) -> Int {
    return when (manga.sorting) {
        Manga.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
            false -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
        }
        Manga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.chapter_number.compareTo(c1.chapter_number) }
            false -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
        }
        Manga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.date_upload.compareTo(c1.date_upload) }
            false -> { c1, c2 -> c1.date_upload.compareTo(c2.date_upload) }
        }
        else -> throw NotImplementedError("Invalid chapter sorting method: ${manga.sorting}")
    }
}

fun getChapterSort(
    manga: DomainManga,
    sortDescending: Boolean = manga.sortDescending(),
): (DomainChapter, DomainChapter) -> Int {
    return when (manga.sorting) {
        DomainManga.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }
        DomainManga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 ->
                c2.chapterNumber.toString().compareToCaseInsensitiveNaturalOrder(c1.chapterNumber.toString())
            }
            false -> { c1, c2 ->
                c1.chapterNumber.toString().compareToCaseInsensitiveNaturalOrder(c2.chapterNumber.toString())
            }
        }
        DomainManga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.dateUpload.compareTo(c1.dateUpload) }
            false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
        }
        else -> throw NotImplementedError("Unimplemented sorting method")
    }
}
