package eu.kanade.domain.updates.repository

import eu.kanade.domain.updates.model.UpdatesWithRelations
import kotlinx.coroutines.flow.Flow

interface UpdatesRepository {

    fun subscribeAll(after: Long): Flow<List<UpdatesWithRelations>>
}
