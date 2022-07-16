package eu.kanade.domain.source.repository

import eu.kanade.domain.source.model.SourceData
import kotlinx.coroutines.flow.Flow

interface SourceDataRepository {
    fun subscribeAll(): Flow<List<SourceData>>

    suspend fun getSourceData(id: Long): SourceData?

    suspend fun upsertSourceData(id: Long, lang: String, name: String)
}
