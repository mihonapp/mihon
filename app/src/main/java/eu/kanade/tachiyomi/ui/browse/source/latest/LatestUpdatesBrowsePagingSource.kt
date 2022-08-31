package eu.kanade.tachiyomi.ui.browse.source.latest

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowsePagingSource
import eu.kanade.tachiyomi.util.lang.awaitSingle

class LatestUpdatesBrowsePagingSource(val source: CatalogueSource) : BrowsePagingSource() {

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.fetchLatestUpdates(currentPage).awaitSingle()
    }
}
