package tachiyomi.data.history

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository

class HistoryRepositoryImpl(
    private val database: Database,
) : HistoryRepository {

    override fun getHistory(query: String): Flow<List<HistoryWithRelations>> {
        return database.historyViewQueries
            .history(query, HistoryMapper::mapHistoryWithRelations)
            .subscribeToList()
    }

    override suspend fun getLastHistory(): HistoryWithRelations? {
        return database.historyViewQueries
            .getLatestHistory(HistoryMapper::mapHistoryWithRelations)
            .awaitAsOneOrNull()
    }

    override suspend fun getTotalReadDuration(): Long {
        return database.historyQueries
            .getReadDuration()
            .awaitAsOne()
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> {
        return database.historyQueries
            .getHistoryByMangaId(mangaId, HistoryMapper::mapHistory)
            .awaitAsList()
    }

    override suspend fun resetHistory(historyId: Long) {
        try {
            database.historyQueries.resetHistoryById(historyId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long) {
        try {
            database.historyQueries.resetHistoryByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            database.historyQueries.removeAllHistory()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate) {
        try {
            database.historyQueries.upsert(
                historyUpdate.chapterId,
                historyUpdate.readAt,
                historyUpdate.sessionReadDuration,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
