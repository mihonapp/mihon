package eu.kanade.data.source

import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.source.SourceLatestPagingSource
import tachiyomi.data.source.SourcePagingSourceType
import tachiyomi.data.source.SourcePopularPagingSource
import tachiyomi.data.source.SourceSearchPagingSource
import tachiyomi.data.source.sourceMapper
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.SourceWithCount
import tachiyomi.source.local.LocalSource

class SourceRepositoryImpl(
    private val sourceManager: SourceManager,
    private val handler: DatabaseHandler,
) : SourceRepository {

    override fun getSources(): Flow<List<Source>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map {
                sourceMapper(it).copy(
                    supportsLatest = it.supportsLatest,
                )
            }
        }
    }

    override fun getOnlineSources(): Flow<List<Source>> {
        return sourceManager.catalogueSources.map { sources ->
            sources
                .filterIsInstance<HttpSource>()
                .map(sourceMapper)
        }
    }

    override fun getSourcesWithFavoriteCount(): Flow<List<Pair<Source, Long>>> {
        val sourceIdWithFavoriteCount = handler.subscribeToList { mangasQueries.getSourceIdWithFavoriteCount() }
        return sourceIdWithFavoriteCount.map { sourceIdsWithCount ->
            sourceIdsWithCount
                .filterNot { it.source == LocalSource.ID }
                .map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = sourceMapper(source).copy(
                        isStub = source is SourceManager.StubSource,
                    )
                    domainSource to count
                }
        }
    }

    override fun getSourcesWithNonLibraryManga(): Flow<List<SourceWithCount>> {
        val sourceIdWithNonLibraryManga = handler.subscribeToList { mangasQueries.getSourceIdsWithNonLibraryManga() }
        return sourceIdWithNonLibraryManga.map { sourceId ->
            sourceId.map { (sourceId, count) ->
                val source = sourceManager.getOrStub(sourceId)
                val domainSource = sourceMapper(source).copy(
                    isStub = source is SourceManager.StubSource,
                )
                SourceWithCount(domainSource, count)
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
