package eu.kanade.data.source

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
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
                .map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId).run {
                        sourceMapper(this)
                    }
                    source to count
                }
                .filterNot { it.first.id == LocalSource.ID }
        }
    }
}
