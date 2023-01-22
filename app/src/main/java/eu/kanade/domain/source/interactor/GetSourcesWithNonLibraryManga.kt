package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.SourceWithCount

class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
) {

    fun subscribe(): Flow<List<SourceWithCount>> {
        return repository.getSourcesWithNonLibraryManga()
    }
}
