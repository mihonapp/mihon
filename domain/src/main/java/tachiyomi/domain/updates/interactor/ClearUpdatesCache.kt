package tachiyomi.domain.updates.interactor

import tachiyomi.domain.updates.repository.UpdatesRepository

class ClearUpdatesCache(
    private val repository: UpdatesRepository,
) {
    suspend fun clearAll() {
        repository.clearAllUpdates()
    }
    
    suspend fun clearOlderThan(timestamp: Long) {
        repository.clearUpdatesOlderThan(timestamp)
    }
}
