package tachiyomi.domain.updates.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.UpdatesWithRelations

interface UpdatesRepository {

    fun subscribeAll(after: Long): Flow<List<UpdatesWithRelations>>
}
