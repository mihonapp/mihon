package eu.kanade.tachiyomi.ui.browse.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateSearchScreenModel(
    val mangaId: Long,
    getManga: GetManga = Injekt.get(),
) : SearchScreenModel() {

    init {
        screenModelScope.launch {
            val manga = getManga.await(mangaId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = manga.source,
                    searchQuery = manga.title,
                )
            }
            search()
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
