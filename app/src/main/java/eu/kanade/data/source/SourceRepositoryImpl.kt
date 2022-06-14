package eu.kanade.data.source

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.model.SourceData
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import eu.kanade.tachiyomi.source.Source as LoadedSource

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

    override fun getSourcesWithNonLibraryManga(): Flow<List<Pair<LoadedSource, Long>>> {
        val sourceIdWithNonLibraryManga = handler.subscribeToList { mangasQueries.getSourceIdsWithNonLibraryManga() }
        return sourceIdWithNonLibraryManga.map { sourceId ->
            sourceId.map { (sourceId, count) ->
                val source = sourceManager.getOrStub(sourceId)
                source to count
            }
        }
    }

    override suspend fun getSourceData(id: Long): SourceData? {
        return handler.awaitOneOrNull { sourcesQueries.getSourceData(id, sourceDataMapper) }
    }

    override suspend fun upsertSourceData(id: Long, lang: String, name: String) {
        handler.await { sourcesQueries.upsert(id, lang, name) }
    }
}
