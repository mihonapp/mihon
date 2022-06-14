package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.SourceData
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class UpsertSourceData(
    private val repository: SourceRepository,
) {

    suspend fun await(sourceData: SourceData) {
        try {
            repository.upsertSourceData(sourceData.id, sourceData.lang, sourceData.name)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
