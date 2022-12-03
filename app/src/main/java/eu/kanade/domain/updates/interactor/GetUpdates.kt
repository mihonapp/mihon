package eu.kanade.domain.updates.interactor

import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class GetUpdates(
    private val repository: UpdatesRepository,
) {

    fun subscribe(calendar: Calendar): Flow<List<UpdatesWithRelations>> = subscribe(calendar.time.time)

    fun subscribe(after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(after)
    }
}
