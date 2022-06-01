package eu.kanade.domain.history.repository

import androidx.paging.PagingSource
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.model.HistoryUpdate
import eu.kanade.domain.history.model.HistoryWithRelations

interface HistoryRepository {

    fun getHistory(query: String): PagingSource<Long, HistoryWithRelations>

    suspend fun getLastHistory(): HistoryWithRelations?

    suspend fun getNextChapter(mangaId: Long, chapterId: Long): Chapter?

    suspend fun resetHistory(historyId: Long)

    suspend fun resetHistoryByMangaId(mangaId: Long)

    suspend fun deleteAllHistory(): Boolean

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)
}
