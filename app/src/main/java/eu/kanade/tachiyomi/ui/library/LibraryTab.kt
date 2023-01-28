package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.DeleteLibraryMangaDialog
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.EmptyScreenAction
import eu.kanade.presentation.components.LibraryBottomActionMenu
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.manga.components.DownloadCustomAmountDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.display
import tachiyomi.domain.manga.model.Manga

object LibraryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 0u,
                title = stringResource(R.string.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val screenModel = rememberScreenModel { LibraryScreenModel() }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = {
            val started = LibraryUpdateService.start(context, it)
            scope.launch {
                val msgRes = if (started) R.string.updating_category else R.string.update_already_running
                snackbarHostState.showSnackbar(context.getString(msgRes))
            }
            started
        }
        val onClickFilter: () -> Unit = {
            scope.launch { sendSettingsSheetIntent(state.categories[screenModel.activeCategoryIndex]) }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                val title = state.getToolbarTitle(
                    defaultTitle = stringResource(R.string.label_library),
                    defaultCategoryTitle = stringResource(R.string.label_default),
                    page = screenModel.activeCategoryIndex,
                )
                val tabVisible = state.showCategoryTabs && state.categories.size > 1
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = state.selection.size,
                    title = title,
                    onClickUnselectAll = screenModel::clearSelection,
                    onClickSelectAll = { screenModel.selectAll(screenModel.activeCategoryIndex) },
                    onClickInvertSelection = { screenModel.invertSelection(screenModel.activeCategoryIndex) },
                    onClickFilter = onClickFilter,
                    onClickRefresh = { onClickRefresh(null) },
                    onClickOpenRandomManga = {
                        scope.launch {
                            val randomItem = screenModel.getRandomLibraryItemForCurrentCategory()
                            if (randomItem != null) {
                                navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.information_no_entries_found))
                            }
                        }
                    },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = screenModel::search,
                    scrollBehavior = scrollBehavior.takeIf { !tabVisible }, // For scroll overlay when no tab
                )
            },
            bottomBar = {
                LibraryBottomActionMenu(
                    visible = state.selectionMode,
                    onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                    onMarkAsReadClicked = { screenModel.markReadSelection(true) },
                    onMarkAsUnreadClicked = { screenModel.markReadSelection(false) },
                    onDownloadClicked = screenModel::runDownloadActionSelection
                        .takeIf { state.selection.fastAll { !it.manga.isLocal() } },
                    onDeleteClicked = screenModel::openDeleteMangaDialog,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
                state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                    val handler = LocalUriHandler.current
                    EmptyScreen(
                        textResource = R.string.information_empty_library,
                        modifier = Modifier.padding(contentPadding),
                        actions = listOf(
                            EmptyScreenAction(
                                stringResId = R.string.getting_started_guide,
                                icon = Icons.Outlined.HelpOutline,
                                onClick = { handler.openUri("https://tachiyomi.org/help/guides/getting-started") },
                            ),
                        ),
                    )
                }
                else -> {
                    LibraryContent(
                        categories = state.categories,
                        searchQuery = state.searchQuery,
                        selection = state.selection,
                        contentPadding = contentPadding,
                        currentPage = { screenModel.activeCategoryIndex },
                        hasActiveFilters = state.hasActiveFilters,
                        showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                        onChangeCurrentPage = { screenModel.activeCategoryIndex = it },
                        onMangaClicked = { navigator.push(MangaScreen(it)) },
                        onContinueReadingClicked = { it: LibraryManga ->
                            scope.launchIO {
                                val chapter = screenModel.getNextUnreadChapter(it.manga)
                                if (chapter != null) {
                                    context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
                                } else {
                                    snackbarHostState.showSnackbar(context.getString(R.string.no_next_chapter))
                                }
                            }
                            Unit
                        }.takeIf { state.showMangaContinueButton },
                        onToggleSelection = { screenModel.toggleSelection(it) },
                        onToggleRangeSelection = {
                            screenModel.toggleRangeSelection(it)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onRefresh = onClickRefresh,
                        onGlobalSearchClicked = {
                            navigator.push(GlobalSearchScreen(screenModel.state.value.searchQuery ?: ""))
                        },
                        getNumberOfMangaForCategory = { state.getMangaCountForCategory(it) },
                        getDisplayModeForPage = { state.categories[it].display },
                        getColumnsForOrientation = { screenModel.getColumnsPreferenceForCurrentOrientation(it) },
                    ) { state.getLibraryItemsByPage(it) }
                }
            }
        }

        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is LibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        screenModel.clearSelection()
                        navigator.push(CategoryScreen())
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setMangaCategories(dialog.manga, include, exclude)
                    },
                )
            }
            is LibraryScreenModel.Dialog.DeleteManga -> {
                DeleteLibraryMangaDialog(
                    containsLocalManga = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        screenModel.clearSelection()
                    },
                )
            }
            is LibraryScreenModel.Dialog.DownloadCustomAmount -> {
                DownloadCustomAmountDialog(
                    maxAmount = dialog.max,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { amount ->
                        screenModel.downloadUnreadChapters(dialog.manga, amount)
                        screenModel.clearSelection()
                    },
                )
            }
            null -> {}
        }

        BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
            when {
                state.selectionMode -> screenModel.clearSelection()
                state.searchQuery != null -> screenModel.search(null)
            }
        }

        LaunchedEffect(state.selectionMode) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { onClickFilter() } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private val openSettingsSheetEvent_ = Channel<Category>()
    val openSettingsSheetEvent = openSettingsSheetEvent_.receiveAsFlow()
    private suspend fun sendSettingsSheetIntent(category: Category) = openSettingsSheetEvent_.send(category)
    suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
