package eu.kanade.domain.source.repository

import eu.kanade.domain.source.model.Source
import kotlinx.coroutines.flow.Flow

interface SourceRepository {

    fun getSources(): Flow<List<Source>>

    fun getOnlineSources(): Flow<List<Source>>

    fun getSourcesWithFavoriteCount(): Flow<List<Pair<Source, Long>>>
}
