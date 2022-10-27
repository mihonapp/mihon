package eu.kanade.data.history

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.history.model.HistoryUpdate
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class HistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : HistoryRepository {

    override fun getHistory(query: String): Flow<List<HistoryWithRelations>> {
        return handler.subscribeToList {
            historyViewQueries.history(query, historyWithRelationsMapper)
        }
    }

    override suspend fun getLastHistory(): HistoryWithRelations? {
        return handler.awaitOneOrNull {
            historyViewQueries.getLatestHistory(historyWithRelationsMapper)
        }
    }

    override suspend fun resetHistory(historyId: Long) {
        try {
            handler.await { historyQueries.resetHistoryById(historyId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long) {
        try {
            handler.await { historyQueries.resetHistoryByMangaId(mangaId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            handler.await { historyQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate) {
        try {
            handler.await {
                historyQueries.upsert(
                    historyUpdate.chapterId,
                    historyUpdate.readAt,
                    historyUpdate.sessionReadDuration,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
