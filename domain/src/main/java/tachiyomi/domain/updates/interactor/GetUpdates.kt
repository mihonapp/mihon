package tachiyomi.domain.updates.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository
import java.util.Calendar

class GetUpdates(
    private val repository: UpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<UpdatesWithRelations> {
        return repository.awaitWithRead(read, after)
    }

    fun subscribe(calendar: Calendar): Flow<List<UpdatesWithRelations>> = subscribe(calendar.time.time)

    fun subscribe(after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(after)
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after)
    }
}
