package eu.kanade.domain.source.repository

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.model.SourceData
import kotlinx.coroutines.flow.Flow
import eu.kanade.tachiyomi.source.Source as LoadedSource

interface SourceRepository {

    fun getSources(): Flow<List<Source>>

    fun getOnlineSources(): Flow<List<Source>>

    fun getSourcesWithFavoriteCount(): Flow<List<Pair<Source, Long>>>

    fun getSourcesWithNonLibraryManga(): Flow<List<Pair<LoadedSource, Long>>>

    suspend fun getSourceData(id: Long): SourceData?

    suspend fun upsertSourceData(id: Long, lang: String, name: String)
}
