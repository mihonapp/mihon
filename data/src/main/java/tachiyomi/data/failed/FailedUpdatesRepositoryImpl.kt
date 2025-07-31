package tachiyomi.data.failed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.failed.model.FailedUpdate
import tachiyomi.domain.failed.repository.FailedUpdatesRepository

class FailedUpdatesRepositoryImpl(
    private val handler: DatabaseHandler,
) : FailedUpdatesRepository {
    override fun getFailedUpdates(): Flow<List<FailedUpdate>> {
        return handler.subscribeToList { failed_updatesQueries.getFailedUpdates(failedUpdatesMapper) }
    }

    override fun hasFailedUpdates(): Flow<Boolean> {
        return handler
            .subscribeToOne { failed_updatesQueries.getFailedUpdatesCount() }
            .map { it > 0 }
            .distinctUntilChanged()
    }

    override suspend fun removeFailedUpdatesByMangaIds(mangaIds: List<Long>) {
        try {
            handler.await { failed_updatesQueries.removeFailedUpdatesByMangaIds(mangaIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun removeAllFailedUpdates() {
        try {
            handler.await { failed_updatesQueries.removeAllFailedUpdates() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun insert(mangaId: Long, errorMessage: String, isOnline: Long) {
        handler.await(inTransaction = true) {
            failed_updatesQueries.insert(
                mangaId = mangaId,
                errorMessage = errorMessage,
                isOnline = isOnline,
            )
        }
    }
}
