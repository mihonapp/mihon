package eu.kanade.tachiyomi.ui.browse.source.browse

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.lang.awaitSingle

class SourceBrowsePagingSource(val source: CatalogueSource, val query: String, val filters: FilterList) : BrowsePagingSource() {

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val observable = if (query.isBlank() && filters.isEmpty()) {
            source.fetchPopularManga(currentPage)
        } else {
            source.fetchSearchManga(currentPage, query, filters)
        }

        return observable.awaitSingle()
            .takeIf { it.mangas.isNotEmpty() } ?: throw NoResultsException()
    }
}
