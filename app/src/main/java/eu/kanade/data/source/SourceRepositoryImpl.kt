package eu.kanade.data.source

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.model.SourcePagingSourceType
import eu.kanade.domain.source.model.SourceWithCount
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SourceRepositoryImpl(
    private val sourceManager: SourceManager,
    private val handler: DatabaseHandler,
) : SourceRepository {

    override fun getSources(): Flow<List<Source>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map(catalogueSourceMapper)
        }
    }

    override fun getOnlineSources(): Flow<List<Source>> {
        return sourceManager.onlineSources.map { sources ->
            sources.map(sourceMapper)
        }
    }

    override fun getSourcesWithFavoriteCount(): Flow<List<Pair<Source, Long>>> {
        val sourceIdWithFavoriteCount = handler.subscribeToList { mangasQueries.getSourceIdWithFavoriteCount() }
        return sourceIdWithFavoriteCount.map { sourceIdsWithCount ->
            sourceIdsWithCount
                .filterNot { it.source == LocalSource.ID }
                .map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId).run {
                        sourceMapper(this)
                    }
                    source to count
                }
        }
    }

    override fun getSourcesWithNonLibraryManga(): Flow<List<SourceWithCount>> {
        val sourceIdWithNonLibraryManga = handler.subscribeToList { mangasQueries.getSourceIdsWithNonLibraryManga() }
        return sourceIdWithNonLibraryManga.map { sourceId ->
            sourceId.map { (sourceId, count) ->
                val source = sourceManager.getOrStub(sourceId)
                SourceWithCount(sourceMapper(source), count)
            }
        }
    }

    override fun search(
        sourceId: Long,
        query: String,
        filterList: FilterList,
    ): SourcePagingSourceType {
        val source = sourceManager.get(sourceId) as CatalogueSource
        return SourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopular(sourceId: Long): SourcePagingSourceType {
        val source = sourceManager.get(sourceId) as CatalogueSource
        return SourcePopularPagingSource(source)
    }

    override fun getLatest(sourceId: Long): SourcePagingSourceType {
        val source = sourceManager.get(sourceId) as CatalogueSource
        return SourceLatestPagingSource(source)
    }
}
