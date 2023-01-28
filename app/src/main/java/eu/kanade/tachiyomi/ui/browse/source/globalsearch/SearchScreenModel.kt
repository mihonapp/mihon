package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.NetworkToLocalManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.util.lang.awaitSingle
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

abstract class SearchScreenModel<T>(
    initialState: T,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
) : StateScreenModel<T>(initialState) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    protected var query: String? = null
    protected lateinit var extensionFilter: String

    private val sources by lazy { getSelectedSources() }
    private val pinnedSources by lazy { sourcePreferences.pinnedSources().get() }

    private val sortComparator = { map: Map<CatalogueSource, SearchItemResult> ->
        compareBy<CatalogueSource>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    @Composable
    fun getManga(source: CatalogueSource, initialManga: Manga): State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    if (manga == null) return@collectLatest
                    withIOContext {
                        initializeManga(source, manga)
                    }
                    value = manga
                }
        }
    }

    /**
     * Initialize a manga.
     *
     * @param source to interact with
     * @param manga to initialize.
     */
    private suspend fun initializeManga(source: CatalogueSource, manga: Manga) {
        if (manga.thumbnailUrl != null || manga.initialized) return
        withNonCancellableContext {
            try {
                val networkManga = source.getMangaDetails(manga.toSManga())
                val updatedManga = manga.copyFrom(networkManga)
                    .copy(initialized = true)

                updateManga.await(updatedManga.toMangaUpdate())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    abstract fun getEnabledSources(): List<CatalogueSource>

    fun getSelectedSources(): List<CatalogueSource> {
        val filter = extensionFilter

        val enabledSources = getEnabledSources()

        if (filter.isEmpty()) {
            val shouldSearchPinnedOnly = sourcePreferences.searchPinnedSourcesOnly().get()
            val pinnedSources = sourcePreferences.pinnedSources().get()

            return enabledSources.filter {
                if (shouldSearchPinnedOnly) {
                    "${it.id}" in pinnedSources
                } else {
                    true
                }
            }
        }

        return extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filter { it in enabledSources }
            .filterIsInstance<CatalogueSource>()
    }

    abstract fun updateSearchQuery(query: String?)

    abstract fun updateItems(items: Map<CatalogueSource, SearchItemResult>)

    abstract fun getItems(): Map<CatalogueSource, SearchItemResult>

    fun getAndUpdateItems(function: (Map<CatalogueSource, SearchItemResult>) -> Map<CatalogueSource, SearchItemResult>) {
        updateItems(function(getItems()))
    }

    fun search(query: String) {
        if (this.query == query) return

        this.query = query

        val initialItems = getSelectedSources().associateWith { SearchItemResult.Loading }
        updateItems(initialItems)

        coroutineScope.launch {
            sources
                .map { source ->
                    async {
                        try {
                            val page = withContext(coroutineDispatcher) {
                                source.fetchSearchManga(1, query, source.getFilterList()).awaitSingle()
                            }

                            val titles = withIOContext {
                                page.mangas.map {
                                    networkToLocalManga.await(it.toDomainManga(source.id))
                                }
                            }

                            getAndUpdateItems { items ->
                                val mutableMap = items.toMutableMap()
                                mutableMap[source] = SearchItemResult.Success(titles)
                                mutableMap.toSortedMap(sortComparator(mutableMap))
                            }
                        } catch (e: Exception) {
                            getAndUpdateItems { items ->
                                val mutableMap = items.toMutableMap()
                                mutableMap[source] = SearchItemResult.Error(throwable = e)
                                mutableMap.toSortedMap(sortComparator(mutableMap))
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }
}

sealed class SearchItemResult {
    object Loading : SearchItemResult()

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult()

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult() {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }
}
