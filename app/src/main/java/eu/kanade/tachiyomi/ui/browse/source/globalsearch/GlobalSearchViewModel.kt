package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class GlobalSearchViewModel(
    @Assisted initialQuery: String,
    @Assisted initialExtensionFilter: String?,
    @Assisted private val linkedSourceSearch: Boolean,
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    preferences: SourcePreferences,
) : SearchViewModel(
    initialState = State(
        searchQuery = initialQuery,
        sourceFilter = if (linkedSourceSearch) SourceFilter.All else SourceFilter.PinnedOnly,
    ),
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
    preferences = preferences,
) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            initialQuery: String,
            initialExtensionFilter: String?,
            linkedSourceSearch: Boolean = false,
        ): GlobalSearchViewModel
    }

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }
    }

    override suspend fun getEnabledSources(): List<Source> {
        return super.getEnabledSources()
            .filter { !linkedSourceSearch || it is HttpSource }
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
