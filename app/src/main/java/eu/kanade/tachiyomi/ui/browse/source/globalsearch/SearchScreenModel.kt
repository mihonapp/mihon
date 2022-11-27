package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.NetworkToLocalManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.domain.manga.model.toMangaUpdate
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.util.lang.awaitSingle
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withNonCancellableContext
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
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

    abstract fun updateItems(items: Map<CatalogueSource, GlobalSearchItemResult>)

    abstract fun getItems(): Map<CatalogueSource, GlobalSearchItemResult>

    fun getAndUpdateItems(function: (Map<CatalogueSource, GlobalSearchItemResult>) -> Map<CatalogueSource, GlobalSearchItemResult>) {
        updateItems(function(getItems()))
    }

    fun search(query: String) {
        if (this.query == query) return

        this.query = query

        val initialItems = getSelectedSources().associateWith { GlobalSearchItemResult.Loading }
        updateItems(initialItems)

        val pinnedSources = sourcePreferences.pinnedSources().get()

        val comparator = { mutableMap: MutableMap<CatalogueSource, GlobalSearchItemResult> ->
            compareBy<CatalogueSource>(
                { mutableMap[it] is GlobalSearchItemResult.Success },
                { "${it.id}" in pinnedSources },
                { "${it.name.lowercase()} (${it.lang})" },
            )
        }

        coroutineScope.launch {
            sources.forEach { source ->
                val page = try {
                    withContext(coroutineDispatcher) {
                        source.fetchSearchManga(1, query, source.getFilterList()).awaitSingle()
                    }
                } catch (e: Exception) {
                    getAndUpdateItems { items ->
                        val mutableMap = items.toMutableMap()
                        mutableMap[source] = GlobalSearchItemResult.Error(throwable = e)
                        mutableMap.toSortedMap(comparator(mutableMap))
                        mutableMap.toMap()
                    }
                    return@forEach
                }

                val titles = page.mangas.map {
                    withIOContext {
                        networkToLocalManga.await(it.toDomainManga(source.id))
                    }
                }

                getAndUpdateItems { items ->
                    val mutableMap = items.toMutableMap()
                    mutableMap[source] = GlobalSearchItemResult.Success(titles)
                    mutableMap.toSortedMap(comparator(mutableMap))
                    mutableMap.toMap()
                }
            }
        }
    }
}
