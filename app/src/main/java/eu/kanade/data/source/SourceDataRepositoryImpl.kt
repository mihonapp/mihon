package eu.kanade.data.source

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.source.model.SourceData
import eu.kanade.domain.source.repository.SourceDataRepository
import kotlinx.coroutines.flow.Flow

class SourceDataRepositoryImpl(
    private val handler: DatabaseHandler,
) : SourceDataRepository {

    override fun subscribeAll(): Flow<List<SourceData>> {
        return handler.subscribeToList { sourcesQueries.findAll(sourceDataMapper) }
    }

    override suspend fun getSourceData(id: Long): SourceData? {
        return handler.awaitOneOrNull { sourcesQueries.findOne(id, sourceDataMapper) }
    }

    override suspend fun upsertSourceData(id: Long, lang: String, name: String) {
        handler.await { sourcesQueries.upsert(id, lang, name) }
    }
}
