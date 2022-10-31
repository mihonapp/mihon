package eu.kanade.domain.history.interactor

import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import kotlin.math.max

class GetNextUnreadChapters(
    private val getChapterByMangaId: GetChapterByMangaId,
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(): Chapter? {
        val history = historyRepository.getLastHistory() ?: return null
        return await(history.mangaId, history.chapterId).firstOrNull()
    }

    suspend fun await(mangaId: Long): List<Chapter> {
        val manga = getManga.await(mangaId) ?: return emptyList()
        return getChapterByMangaId.await(mangaId)
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .filterNot { it.read }
    }

    suspend fun await(mangaId: Long, fromChapterId: Long): List<Chapter> {
        val unreadChapters = await(mangaId)
        val currChapterIndex = unreadChapters.indexOfFirst { it.id == fromChapterId }
        return unreadChapters.subList(max(0, currChapterIndex), unreadChapters.size)
    }
}
