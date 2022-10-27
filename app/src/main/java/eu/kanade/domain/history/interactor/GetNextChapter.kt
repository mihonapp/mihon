package eu.kanade.domain.history.interactor

import eu.kanade.domain.chapter.interactor.GetChapter
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.util.chapter.getChapterSort

class GetNextChapter(
    private val getChapter: GetChapter,
    private val getChapterByMangaId: GetChapterByMangaId,
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(): Chapter? {
        val history = historyRepository.getLastHistory() ?: return null
        return await(history.mangaId, history.chapterId)
    }

    suspend fun await(mangaId: Long, chapterId: Long): Chapter? {
        val chapter = getChapter.await(chapterId)!!
        val manga = getManga.await(mangaId)!!

        if (!chapter.read) return chapter

        val chapters = getChapterByMangaId.await(mangaId)
            .sortedWith(getChapterSort(manga, sortDescending = false))

        val currChapterIndex = chapters.indexOfFirst { chapter.id == it.id }
        return when (manga.sorting) {
            Manga.CHAPTER_SORTING_SOURCE -> chapters.getOrNull(currChapterIndex + 1)
            Manga.CHAPTER_SORTING_NUMBER -> {
                val chapterNumber = chapter.chapterNumber

                ((currChapterIndex + 1) until chapters.size)
                    .map { chapters[it] }
                    .firstOrNull {
                        it.chapterNumber > chapterNumber && it.chapterNumber <= chapterNumber + 1
                    }
            }
            Manga.CHAPTER_SORTING_UPLOAD_DATE -> {
                chapters.drop(currChapterIndex + 1)
                    .firstOrNull { it.dateUpload >= chapter.dateUpload }
            }
            else -> throw NotImplementedError("Invalid chapter sorting method: ${manga.sorting}")
        }
    }
}
