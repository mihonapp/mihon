package tachiyomi.domain.updates.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.UpdatesWithRelations

interface UpdatesRepository {

    suspend fun awaitWithRead(read: Boolean, after: Long): List<UpdatesWithRelations>

    fun subscribeAll(after: Long, limit: Long): Flow<List<UpdatesWithRelations>>

    fun subscribeWithRead(read: Boolean, after: Long): Flow<List<UpdatesWithRelations>>
}
