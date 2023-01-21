package eu.kanade.data.updates

import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler

class UpdatesRepositoryImpl(
    val databaseHandler: DatabaseHandler,
) : UpdatesRepository {

    override fun subscribeAll(after: Long): Flow<List<UpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.updates(after, updateWithRelationMapper)
        }
    }
}
