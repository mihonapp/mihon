package tachiyomi.domain.source.interactor

import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.source.repository.SourcePagingSourceType
import tachiyomi.domain.source.repository.SourceRepository

class GetRemoteManga(
    private val repository: SourceRepository,
) {

    fun subscribe(sourceId: Long, query: String, filterList: FilterList): SourcePagingSourceType {
        return when (query) {
            QUERY_POPULAR -> repository.getPopular(sourceId)
            QUERY_LATEST -> repository.getLatest(sourceId)
            else -> repository.search(sourceId, query, filterList)
        }
    }

    companion object {
        const val QUERY_POPULAR = "eu.kanade.domain.source.interactor.POPULAR"
        const val QUERY_LATEST = "eu.kanade.domain.source.interactor.LATEST"
    }
}
