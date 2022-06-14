package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.SourceData
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class GetSourceData(
    private val repository: SourceRepository,
) {

    suspend fun await(id: Long): SourceData? {
        return try {
            repository.getSourceData(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }
}
