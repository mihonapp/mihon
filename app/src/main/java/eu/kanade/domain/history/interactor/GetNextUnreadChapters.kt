package eu.kanade.domain.history.interactor

import eu.kanade.domain.chapter.interactor.GetChapter
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.tachiyomi.util.chapter.getChapterSort

class GetNextUnreadChapters(
    private val getChapter: GetChapter,
    private val getChapterByMangaId: GetChapterByMangaId,
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(): Chapter? {
        val history = historyRepository.getLastHistory() ?: return null
        return await(history.mangaId, history.chapterId).firstOrNull()
    }

    suspend fun await(mangaId: Long, chapterId: Long): List<Chapter> {
        val chapter = getChapter.await(chapterId) ?: return emptyList()
        val manga = getManga.await(mangaId) ?: return emptyList()

        val chapters = getChapterByMangaId.await(mangaId)
            .sortedWith(getChapterSort(manga, sortDescending = false))
        val currChapterIndex = chapters.indexOfFirst { chapter.id == it.id }
        return chapters
            .subList(currChapterIndex, chapters.size)
            .filterNot { it.read }
    }
}
