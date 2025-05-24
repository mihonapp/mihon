package eu.kanade.tachiyomi.ui.browse.migration.advanced.process.smartsearch

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga

class SmartSourceSearchEngine(
    extraSearchParams: String? = null,
) : BaseSmartSearchEngine<SManga>(extraSearchParams) {

    override fun getTitle(result: SManga) = result.title

    suspend fun smartSearch(source: CatalogueSource, title: String): Manga? =
        smartSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }

    suspend fun normalSearch(source: CatalogueSource, title: String): Manga? =
        normalSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }

    private fun makeSearchAction(source: CatalogueSource): SearchAction<SManga> =
        { query -> source.getSearchManga(1, query, FilterList()).mangas }
}
