package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.SourceWithCount
import eu.kanade.domain.source.repository.SourceRepository
import kotlinx.coroutines.flow.Flow

class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
) {

    fun subscribe(): Flow<List<SourceWithCount>> {
        return repository.getSourcesWithNonLibraryManga()
    }
}
