package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.library.DeleteLibraryMangaDialog
import eu.kanade.presentation.library.LibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.manga.components.LibraryBottomActionMenu
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.feature.mekuri.shelf.CompactShelf
import mihon.feature.mekuri.shelf.ResumeInfo
import mihon.feature.mekuri.shelf.Shelf
import mihon.feature.mekuri.shelf.ShelfActionsViewModel
import mihon.feature.mekuri.shelf.ShelfDeck
import mihon.feature.mekuri.shelf.ShelfMenuSheet
import mihon.feature.mekuri.shelf.ShelfPrefs
import mihon.feature.mekuri.shelf.SmartShelf
import mihon.feature.mekuri.shelf.SmartShelfScreen
import mihon.feature.mekuri.shelf.VAULT_SPINE_KEY
import mihon.feature.mekuri.vault.MekuriVault
import mihon.feature.mekuri.vault.unlockMekuriVault
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Help
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

data object LibraryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.animated_library)
            return TabOptions(
                index = 0u,
                title = stringResource(MR.strings.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        // Mekuri: inside a shelf, the Library button goes back to the bookshelf
        if (inShelfCategory) showBookshelfEvent.send(Unit) else requestOpenSettingsSheet()
    }

    // Mekuri: bookshelf state shared with onReselect
    @Volatile
    private var inShelfCategory = false
    private val showBookshelfEvent = Channel<Unit>()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val viewModel = metroViewModel<LibraryViewModel>()
        val settingsViewModel = metroViewModel<LibrarySettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }

        // Mekuri: the bookshelf home (Settings > Library > Classic library turns it off)
        val classicLibrary by context.appGraph.libraryPreferences.mekuriClassicLibrary.collectAsState()
        var onBookshelf by rememberSaveable { mutableStateOf(true) }
        val defaultCategoryName = stringResource(MR.strings.label_default)
        val vault by MekuriVault.filter.collectAsState()
        val weekAgo = remember(state.libraryData) { kotlin.time.Clock.System.now() - 7.days }
        val shelves = remember(state.displayedCategories, state.libraryData, defaultCategoryName, vault.categories) {
            state.displayedCategories.mapIndexed { index, category ->
                val items = state.getItemsForCategory(category)
                Shelf(
                    key = category.id,
                    name = if (category.isSystemCategory) defaultCategoryName else category.name,
                    count = items.size,
                    unread = items.count { it.unreadCount > 0 },
                    coverManga = items
                        .maxByOrNull { it.libraryManga.lastRead?.toEpochMilliseconds() ?: 0L }
                        ?.libraryManga?.manga,
                    covers = items
                        .sortedByDescending { it.libraryManga.lastRead?.toEpochMilliseconds() ?: 0L }
                        .take(3)
                        .map { it.libraryManga.manga },
                    categoryIndex = index,
                    kind = if (category.id in vault.categories) Shelf.Kind.Hidden else Shelf.Kind.Normal,
                    // Recently updated: new chapters fetched by an update in the last 7 days (not when the manga was
                    // added), still unread and not read since
                    hasUpdates = items.any { item ->
                        val manga = item.libraryManga
                        val fetched = manga.chapterFetchedAt ?: return@any false
                        val added = manga.manga.favoriteAt
                        item.unreadCount > 0 &&
                            fetched > weekAgo &&
                            (added == null || fetched > added + 10.minutes) &&
                            (manga.lastRead == null || fetched > manga.lastRead!!)
                    },
                )
            }
        }
        // Smart shelves (Recently read, New this week, Unread, Downloaded) fill themselves from the library
        val smartEnabled by ShelfPrefs.smartShelves.collectAsState()
        val smartShelves = remember(state.libraryData, vault, smartEnabled) {
            if (!smartEnabled) return@remember emptyList()
            val items = state.libraryData.favorites
                .distinctBy { it.id }
                .filterNot { vault.hides(it.id, it.libraryManga.manga.source) }
            SmartShelf.entries.mapNotNull { smart ->
                val ids = smart.select(items)
                if (ids.isEmpty()) return@mapNotNull null
                val cover = items.firstOrNull { it.id == ids.first() }?.libraryManga?.manga
                Shelf(
                    key = smart.key,
                    name = smart.title,
                    count = ids.size,
                    unread = 0,
                    coverManga = cover,
                    kind = Shelf.Kind.Smart,
                    smart = smart,
                    mangaIds = ids,
                )
            }
        }
        // Continue reading: the library manga read most recently (vault manga left out while locked)
        val resume = remember(state.libraryData, vault) {
            state.libraryData.favorites
                .filter { it.libraryManga.lastRead != null && !vault.hides(it.id, it.libraryManga.manga.source) }
                .maxByOrNull { it.libraryManga.lastRead!! }
                ?.let { item ->
                    val lastRead = item.libraryManga.lastRead!!
                    val ago = kotlin.time.Clock.System.now() - lastRead
                    ResumeInfo(
                        manga = item.libraryManga.manga,
                        readCount = item.libraryManga.readCount,
                        totalChapters = item.libraryManga.totalChapters,
                        lastReadLabel = when {
                            ago.inWholeMinutes < 1 -> "just now"
                            ago.inWholeHours < 1 -> "${ago.inWholeMinutes} min ago"
                            ago.inWholeDays < 1 -> "${ago.inWholeHours} h ago"
                            ago.inWholeDays == 1L -> "yesterday"
                            else -> "${ago.inWholeDays} days ago"
                        },
                    )
                }
        }
        // The big shelf: smart shelves, regular shelves, vault shelves (only while unlocked), then the vault spine
        val vaultSpineName = "Vault"
        val bigShelves = remember(shelves, smartShelves, vaultSpineName) {
            smartShelves +
                shelves.filter { it.kind == Shelf.Kind.Normal } +
                shelves.filter { it.kind == Shelf.Kind.Hidden } +
                Shelf(
                    key = VAULT_SPINE_KEY,
                    name = vaultSpineName,
                    count = 0,
                    unread = 0,
                    coverManga = null,
                    kind = Shelf.Kind.Vault,
                )
        }
        var focusFirstHidden by remember { mutableStateOf(false) }
        var menuShelf by remember { mutableStateOf<Shelf?>(null) }
        var compactPreview by remember { mutableStateOf<Int?>(null) } // shelf under the finger on the small shelf
        val shelfActions = metroViewModel<ShelfActionsViewModel>()
        val lastShelfIdPref = context.appGraph.libraryPreferences.mekuriLastShelfId
        val lastShelfId by lastShelfIdPref.collectAsState()
        val useBookshelf = !classicLibrary && (shelves.size > 1 || vault.categories.isNotEmpty())
        val showBookshelf = useBookshelf && onBookshelf && state.searchQuery.isNullOrEmpty() && !state.selectionMode
        SideEffect { inShelfCategory = useBookshelf && !onBookshelf }

        val onClickRefresh: suspend (Category?) -> Boolean = { category ->
            val started = LibraryUpdateWorker.startNow(context.workManager, category)
            scope.launch {
                val msgRes = when {
                    !started -> MR.strings.update_already_running
                    category != null -> MR.strings.updating_category
                    else -> MR.strings.updating_library
                }
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        Scaffold(
            topBar = { scrollBehavior ->
                val title = when {
                    // Mekuri: "Library" on the bookshelf, the shelf's name inside it
                    showBookshelf -> LibraryToolbarTitle(stringResource(MR.strings.label_library))
                    useBookshelf -> LibraryToolbarTitle(
                        shelves.getOrNull(compactPreview ?: state.coercedActiveCategoryIndex)?.name
                            ?: stringResource(MR.strings.label_library),
                    )
                    else -> state.getToolbarTitle(
                        defaultTitle = stringResource(MR.strings.label_library),
                        defaultCategoryTitle = stringResource(MR.strings.label_default),
                        page = state.coercedActiveCategoryIndex,
                    )
                }
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = state.selection.size,
                    title = title,
                    onClickUnselectAll = viewModel::clearSelection,
                    onClickSelectAll = viewModel::selectAll,
                    onClickInvertSelection = viewModel::invertSelection,
                    onClickFilter = viewModel::showSettingsDialog,
                    onClickRefresh = { scope.launch { onClickRefresh(state.activeCategory) } },
                    onClickGlobalUpdate = { scope.launch { onClickRefresh(null) } },
                    onClickOpenRandomManga = {
                        scope.launch {
                            val randomItem = viewModel.getRandomLibraryItemForCurrentCategory()
                            if (randomItem != null) {
                                navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = viewModel::search,
                    // For scroll overlay when no tab
                    scrollBehavior = scrollBehavior.takeIf { !state.showCategoryTabs && !useBookshelf },
                )
            },
            bottomBar = {
                LibraryBottomActionMenu(
                    visible = state.selectionMode,
                    onChangeCategoryClicked = viewModel::openChangeCategoryDialog,
                    onMarkAsReadClicked = { viewModel.markReadSelection(true) },
                    onMarkAsUnreadClicked = { viewModel.markReadSelection(false) },
                    onDownloadClicked = viewModel::performDownloadAction
                        .takeIf { state.selectedManga.fastAll { !it.isLocal() } },
                    onDeleteClicked = viewModel::openDeleteMangaDialog,
                    onMigrateClicked = {
                        val selection = state.selection
                        viewModel.clearSelection()
                        navigator.push(MigrationConfigScreen(selection))
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> {
                    LoadingScreen(Modifier.padding(contentPadding))
                }
                state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                    val handler = LocalUriHandler.current
                    EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.padding(contentPadding),
                        actions = listOf(
                            EmptyScreenAction(
                                stringRes = MR.strings.getting_started_guide,
                                icon = MaterialSymbols.AutoMirroredRounded.Help,
                                onClick = { handler.openUri(GETTING_STARTED_URL) },
                            ),
                        ),
                    )
                }
                showBookshelf -> {
                    val firstHidden = bigShelves.indexOfFirst { it.kind == Shelf.Kind.Hidden }
                    ShelfDeck(
                        shelves = bigShelves,
                        resume = resume,
                        onResume = {
                            val manga = resume?.manga ?: return@ShelfDeck
                            scope.launch {
                                val chapter = viewModel.getNextUnreadChapter(manga)
                                if (chapter != null) {
                                    context.startActivity(
                                        ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                    )
                                } else {
                                    navigator.push(MangaScreen(manga.id))
                                }
                            }
                        },
                        onHistory = { navigator.push(HistoryTab) },
                        lastOpenedIndex = if (focusFirstHidden && firstHidden >= 0) {
                            firstHidden
                        } else {
                            bigShelves.indexOfFirst { it.key == lastShelfId }
                                .takeIf { it >= 0 }
                                ?: bigShelves.indexOfFirst { it.categoryIndex == state.coercedActiveCategoryIndex }
                                    .coerceAtLeast(0)
                        },
                        onOpen = {
                            val shelf = bigShelves[it]
                            focusFirstHidden = false
                            lastShelfIdPref.set(shelf.key)
                            val smart = shelf.smart
                            if (smart != null) {
                                navigator.push(SmartShelfScreen(smart, shelf.mangaIds))
                            } else {
                                viewModel.updateActiveCategoryIndex(shelf.categoryIndex)
                                onBookshelf = false
                            }
                        },
                        onShelfMenu = {
                            menuShelf = bigShelves[it]
                            lastShelfIdPref.set(bigShelves[it].key)
                        },
                        contentPadding = contentPadding,
                        vaultUnlocked = vault.unlocked,
                        onVault = {
                            if (vault.unlocked) {
                                focusFirstHidden = false
                                MekuriVault.lock()
                            } else {
                                scope.launch { if (context.unlockMekuriVault()) focusFirstHidden = true }
                            }
                        },
                    )
                }
                else -> {
                    LibraryContent(
                        categories = state.displayedCategories,
                        searchQuery = state.searchQuery,
                        selection = state.selection,
                        contentPadding = contentPadding,
                        currentPage = state.coercedActiveCategoryIndex,
                        hasActiveFilters = state.hasActiveFilters,
                        showPageTabs = useBookshelf || state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                        onChangeCurrentPage = viewModel::updateActiveCategoryIndex,
                        onClickManga = { navigator.push(MangaScreen(it)) },
                        onContinueReadingClicked = { it: LibraryManga ->
                            scope.launch {
                                val chapter = viewModel.getNextUnreadChapter(it.manga)
                                if (chapter != null) {
                                    context.startActivity(
                                        ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
                                }
                            }
                            Unit
                        }.takeIf { state.showMangaContinueButton },
                        onToggleSelection = viewModel::toggleSelection,
                        onToggleRangeSelection = { category, manga ->
                            viewModel.toggleRangeSelection(category, manga)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onRefresh = { onClickRefresh(state.activeCategory) },
                        onGlobalSearchClicked = {
                            navigator.push(GlobalSearchScreen(viewModel.state.value.searchQuery ?: ""))
                        },
                        getItemCountForCategory = { state.getItemCountForCategory(it) },
                        getDisplayMode = { viewModel.getDisplayMode() },
                        getColumnsForOrientation = { viewModel.getColumnsForOrientation(it) },
                        getItemsForCategory = { state.getItemsForCategory(it) },
                        tabsOverride = if (useBookshelf) {
                            { page, scrollTo ->
                                CompactShelf(
                                    shelves = shelves,
                                    currentIndex = page,
                                    onSelect = scrollTo,
                                    onPreview = { compactPreview = it },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        val onDismissRequest = viewModel::closeDialog
        when (val dialog = state.dialog) {
            is LibraryViewModel.Dialog.SettingsSheet -> run {
                LibrarySettingsDialog(
                    onDismissRequest = onDismissRequest,
                    viewModel = settingsViewModel,
                    category = state.activeCategory,
                )
            }
            is LibraryViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        viewModel.clearSelection()
                        navigator.push(CategoryScreen())
                    },
                    onConfirm = { include, exclude ->
                        viewModel.clearSelection()
                        viewModel.setMangaCategories(dialog.manga, include, exclude)
                    },
                )
            }
            is LibraryViewModel.Dialog.DeleteManga -> {
                DeleteLibraryMangaDialog(
                    containsLocalManga = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        viewModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        viewModel.clearSelection()
                    },
                )
            }
            null -> {}
        }

        BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
            when {
                state.selectionMode -> viewModel.clearSelection()
                state.searchQuery != null -> viewModel.search(null)
            }
        }

        // Mekuri: shelf options sheet
        menuShelf?.let { shelf ->
            val userShelves = shelves.filter { it.key > 0 }
            val position = userShelves.indexOfFirst { it.key == shelf.key }
            ShelfMenuSheet(
                shelf = shelves.firstOrNull { it.key == shelf.key } ?: shelf,
                canMoveLeft = position > 0,
                canMoveRight = position in 0 until userShelves.lastIndex,
                onDismiss = { menuShelf = null },
                actions = shelfActions,
            )
        }

        // Mekuri: Back inside a shelf returns to the bookshelf
        BackHandler(enabled = useBookshelf && !onBookshelf && !state.selectionMode && state.searchQuery == null) {
            onBookshelf = true
        }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }
        // Lets startup be measured up to the library, not just its loading screen
        ReportDrawnWhen { !state.isLoading }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(viewModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { viewModel.showSettingsDialog() } }
            launch { showBookshelfEvent.receiveAsFlow().collectLatest { onBookshelf = true } } // Mekuri
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
