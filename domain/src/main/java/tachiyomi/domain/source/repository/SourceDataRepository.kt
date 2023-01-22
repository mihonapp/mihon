package tachiyomi.domain.source.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.SourceData

interface SourceDataRepository {
    fun subscribeAll(): Flow<List<SourceData>>

    suspend fun getSourceData(id: Long): SourceData?

    suspend fun upsertSourceData(id: Long, lang: String, name: String)
}
