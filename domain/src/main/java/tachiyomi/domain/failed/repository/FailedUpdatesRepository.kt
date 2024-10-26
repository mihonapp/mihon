package tachiyomi.domain.failed.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.failed.model.FailedUpdate

interface FailedUpdatesRepository {
    fun getFailedUpdates(): Flow<List<FailedUpdate>>

    fun hasFailedUpdates(): Flow<Boolean>

    suspend fun removeFailedUpdatesByMangaIds(mangaIds: List<Long>)

    suspend fun removeAllFailedUpdates()

    suspend fun insert(mangaId: Long, errorMessage: String, isOnline: Long)
}
