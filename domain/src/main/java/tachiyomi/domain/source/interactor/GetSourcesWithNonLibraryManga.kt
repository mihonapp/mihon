package tachiyomi.domain.source.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.SourceWithCount
import tachiyomi.domain.source.repository.SourceRepository

class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
) {

    fun subscribe(): Flow<List<SourceWithCount>> {
        return repository.getSourcesWithNonLibraryManga()
    }
}
