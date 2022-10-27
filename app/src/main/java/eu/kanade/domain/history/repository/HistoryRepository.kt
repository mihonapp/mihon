package eu.kanade.domain.history.repository

import eu.kanade.domain.history.model.HistoryUpdate
import eu.kanade.domain.history.model.HistoryWithRelations
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {

    fun getHistory(query: String): Flow<List<HistoryWithRelations>>

    suspend fun getLastHistory(): HistoryWithRelations?

    suspend fun resetHistory(historyId: Long)

    suspend fun resetHistoryByMangaId(mangaId: Long)

    suspend fun deleteAllHistory(): Boolean

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)
}
