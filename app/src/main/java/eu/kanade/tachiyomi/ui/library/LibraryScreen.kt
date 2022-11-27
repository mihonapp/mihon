package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.ScaffoldDefaults
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bluelinelabs.conductor.Router
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.library.model.display
import eu.kanade.domain.manga.model.Manga
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
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object LibraryScreen : Screen {

    @Composable
    override fun Content() {
        val router = LocalRouter.currentOrThrow
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
            scope.launch { sendSettingsSheetIntent(state.categories[screenModel.activeCategory]) }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                val title = state.getToolbarTitle(
                    defaultTitle = stringResource(R.string.label_library),
                    defaultCategoryTitle = stringResource(R.string.label_default),
                    page = screenModel.activeCategory,
                )
                val tabVisible = state.showCategoryTabs && state.categories.size > 1
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = state.selection.size,
                    title = title,
                    incognitoMode = !tabVisible && screenModel.isIncognitoMode,
                    downloadedOnlyMode = !tabVisible && screenModel.isDownloadOnly,
                    onClickUnselectAll = screenModel::clearSelection,
                    onClickSelectAll = { screenModel.selectAll(screenModel.activeCategory) },
                    onClickInvertSelection = { screenModel.invertSelection(screenModel.activeCategory) },
                    onClickFilter = onClickFilter,
                    onClickRefresh = { onClickRefresh(null) },
                    onClickOpenRandomManga = {
                        scope.launch {
                            val randomItem = screenModel.getRandomLibraryItemForCurrentCategory()
                            if (randomItem != null) {
                                router.openManga(randomItem.libraryManga.manga.id)
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
            contentWindowInsets = TachiyomiBottomNavigationView.withBottomNavInset(ScaffoldDefaults.contentWindowInsets),
        ) { contentPadding ->
            if (state.isLoading) {
                LoadingScreen(modifier = Modifier.padding(contentPadding))
                return@Scaffold
            }

            if (state.searchQuery.isNullOrEmpty() && state.libraryCount == 0) {
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
                return@Scaffold
            }

            LibraryContent(
                categories = state.categories,
                searchQuery = state.searchQuery,
                selection = state.selection,
                contentPadding = contentPadding,
                currentPage = { screenModel.activeCategory },
                isLibraryEmpty = state.libraryCount == 0,
                showPageTabs = state.showCategoryTabs,
                onChangeCurrentPage = { screenModel.activeCategory = it },
                onMangaClicked = { router.openManga(it) },
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
                    router.pushController(GlobalSearchController(screenModel.state.value.searchQuery ?: ""))
                },
                getNumberOfMangaForCategory = { state.getMangaCountForCategory(it) },
                getDisplayModeForPage = { state.categories[it].display },
                getColumnsForOrientation = { screenModel.getColumnsPreferenceForCurrentOrientation(it) },
                getLibraryForPage = { state.getLibraryItemsByPage(it) },
                isDownloadOnly = screenModel.isDownloadOnly,
                isIncognitoMode = screenModel.isIncognitoMode,
            )
        }

        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is LibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        screenModel.clearSelection()
                        router.pushController(CategoryController())
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
            // Could perhaps be removed when navigation is in a Compose world
            if (router.backstackSize == 1) {
                (context as? MainActivity)?.showBottomNav(!state.selectionMode)
            }
        }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.collectLatest(screenModel::search) }
            launch { requestSettingsSheetEvent.collectLatest { onClickFilter() } }
        }
    }

    private fun Router.openManga(mangaId: Long) {
        pushController(MangaController(mangaId))
    }

    // For invoking search from other screen
    private val queryEvent = MutableSharedFlow<String>(replay = 1)
    fun search(query: String) = queryEvent.tryEmit(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = MutableSharedFlow<Unit>()
    private val openSettingsSheetEvent_ = MutableSharedFlow<Category>()
    val openSettingsSheetEvent = openSettingsSheetEvent_.asSharedFlow()
    private suspend fun sendSettingsSheetIntent(category: Category) = openSettingsSheetEvent_.emit(category)
    suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.emit(Unit)
}
