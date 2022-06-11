package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow

class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
) {

    fun subscribe(): Flow<List<Pair<Source, Long>>> {
        return repository.getSourcesWithNonLibraryManga()
    }
}
