package eu.kanade.data.source

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SourceRepositoryImpl(
    private val sourceManager: SourceManager
) : SourceRepository {

    override fun getSources(): Flow<List<Source>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map(sourceMapper)
        }
    }
}
