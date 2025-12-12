package eu.kanade.tachiyomi.ui.storage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastDistinctBy
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.more.storage.StorageScreenState
import eu.kanade.presentation.more.storage.data.StorageData
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.storage.size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

class StorageScreenModel(
    private val downloadCache: DownloadCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourceFileSystem: LocalSourceFileSystem = Injekt.get(),
) : StateScreenModel<StorageScreenState>(StorageScreenState.Loading(0)) {
    private val _selectedCategory = MutableStateFlow<Category>(allCategory)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val skipDownloadChangeFlow = MutableStateFlow(false)
    private val _downloadedItems = MutableStateFlow<Pair<List<StorageData>, List<Category>>>(
        emptyList<StorageData>() to emptyList(),
    )
    val downloadedItems = _downloadedItems.asStateFlow()

    private val entries = MutableStateFlow<List<Long>>(emptyList())

    init {
        screenModelScope.launchIO {
            val downloadCacheFlow = downloadCache.changes
                .debounce(500L)
                .transformLatest {
                    if (skipDownloadChangeFlow.value) {
                        skipDownloadChangeFlow.value = false
                        return@transformLatest
                    } else {
                        emit(Unit)
                    }
                }

            combine(
                downloadCacheFlow,
                downloadCache.isInitializing,
                getLibraryManga.subscribe().distinctUntilChanged { old, new ->
                    old.map { Pair(it.id, it.category) }.toSet() == new.map { Pair(it.id, it.category) }.toSet()
                },
                getCategories.subscribe(),
            ) { _, _, libraries, categories ->
                val distinctEntries = libraries.fastDistinctBy {
                    it.id
                }

                // If a manga is removed from the list, we don't want to recompute the size for all entries,
                // just remove the entry from the list
                if (downloadedItems.value.first.isNotEmpty() && distinctEntries.size < entries.value.size) {
                    val (items, categories) = downloadedItems.value
                    val libraryIds = libraries.map { it.manga.id }
                    val newItems = items.filter { it.manga.id in libraryIds }

                    entries.value = distinctEntries.map { it.id }

                    return@combine newItems to categories
                }

                entries.value = distinctEntries.map { it.id }

                val items = mutableListOf<StorageData>()

                mutableState.update {
                    StorageScreenState.Loading(0)
                }

                distinctEntries.forEachIndexed { index, libraryManga ->
                    val manga = libraryManga.manga
                    val random = Random(manga.id)

                    val size = getSize(manga)
                    val chapterCount = getCount(manga)
                    val categories = getMangaCategoryIds(manga)

                    mutableState.update {
                        StorageScreenState.Loading((((index + 1.0) / distinctEntries.size) * 100).toInt())
                    }

                    if (size > 0) {
                        items.add(
                            StorageData(
                                manga = manga,
                                categories = categories,
                                size = size,
                                chapterCount = chapterCount,
                                color = Color(
                                    random.nextInt(255),
                                    random.nextInt(255),
                                    random.nextInt(255),
                                ),
                            ),
                        )
                    }
                }
                items to listOf(allCategory) + categories
            }
                .collectLatest {
                    _downloadedItems.value = it
                }
        }

        combine(
            downloadedItems,
            selectedCategory,
        ) { (items, categories), selectedCategory ->
            val filteredItems = if (selectedCategory.id == allCategory.id) {
                items
            } else {
                items.filter { item ->
                    item.categories.contains(selectedCategory.id)
                }
            }
                .sortedByDescending { it.size }

            filteredItems to categories
        }
            .onEach { (items, categories) ->
                if (items.isEmpty() && categories.isEmpty()) return@onEach

                mutableState.update {
                    StorageScreenState.Success(
                        items = items,
                        categories = categories,
                    )
                }
            }
            .launchIn(screenModelScope)
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    private fun getSize(manga: Manga): Long {
        return if (manga.isLocal()) {
            sourceFileSystem
                .getMangaDirectory(manga.url)
                ?.size()
                ?: 0L
        } else {
            downloadManager.getDownloadSize(manga)
        }
    }

    private fun getCount(manga: Manga): Int {
        return if (manga.isLocal()) {
            sourceFileSystem
                .getFilesInMangaDirectory(manga.url)
                .count { Archive.isSupported(it) }
        } else {
            downloadManager.getDownloadCount(manga)
        }
    }

    fun setSelectedCategory(category: Category) {
        _selectedCategory.update { category }
    }

    fun deleteManga(storageData: StorageData, removeFromLibrary: Boolean) {
        val manga = storageData.manga

        screenModelScope.launchNonCancellable {
            skipDownloadChangeFlow.value = true

            if (manga.isLocal()) {
                sourceFileSystem
                    .getMangaDirectory(manga.url)
                    ?.delete()
            } else {
                val source = sourceManager.get(manga.source) ?: return@launchNonCancellable
                downloadManager.deleteManga(manga, source)
            }

            if (removeFromLibrary) {
                updateManga.awaitUpdateFavorite(storageData.manga.id, false)
            }
        }

        _downloadedItems.update { (items, categories) ->
            items.filterNot { it.manga.id == manga.id } to categories
        }
    }

    companion object {
        /**
         * A dummy category used to display all entries irrespective of the category.
         */
        const val ALL_CATEGORY_ID = -1L

        val allCategory = Category(
            id = ALL_CATEGORY_ID,
            name = "All",
            order = 0L,
            flags = 0L,
        )
    }
}
