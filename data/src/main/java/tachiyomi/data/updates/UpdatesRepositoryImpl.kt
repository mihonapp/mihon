package tachiyomi.data.updates

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository

class UpdatesRepositoryImpl(
    private val databaseHandler: DatabaseHandler,
) : UpdatesRepository {

    override suspend fun awaitWithRead(read: Boolean, after: Long, limit: Long): List<UpdatesWithRelations> {
        return databaseHandler.awaitList {
            updatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = updateWithRelationMapper,
            )
        }
    }

    override fun subscribeAll(after: Long, limit: Long): Flow<List<UpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.getRecentUpdates(after, limit, updateWithRelationMapper)
        }
    }

    override fun subscribeWithRead(read: Boolean, after: Long, limit: Long): Flow<List<UpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = updateWithRelationMapper,
            )
        }
    }
}
