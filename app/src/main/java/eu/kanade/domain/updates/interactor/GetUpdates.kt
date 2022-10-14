package eu.kanade.domain.updates.interactor

import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.Calendar

class GetUpdates(
    private val repository: UpdatesRepository,
    private val preferences: LibraryPreferences,
) {

    fun subscribe(calendar: Calendar): Flow<List<UpdatesWithRelations>> = subscribe(calendar.time.time)

    fun subscribe(after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(after)
            .onEach { updates ->
                // Set unread chapter count for bottom bar badge
                preferences.unreadUpdatesCount().set(updates.count { !it.read })
            }
    }
}
