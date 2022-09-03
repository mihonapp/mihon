package eu.kanade.domain.source.repository

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.model.SourcePagingSourceType
import eu.kanade.domain.source.model.SourceWithCount
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.flow.Flow

interface SourceRepository {

    fun getSources(): Flow<List<Source>>

    fun getOnlineSources(): Flow<List<Source>>

    fun getSourcesWithFavoriteCount(): Flow<List<Pair<Source, Long>>>

    fun getSourcesWithNonLibraryManga(): Flow<List<SourceWithCount>>

    fun search(sourceId: Long, query: String, filterList: FilterList): SourcePagingSourceType

    fun getPopular(sourceId: Long): SourcePagingSourceType

    fun getLatest(sourceId: Long): SourcePagingSourceType
}
