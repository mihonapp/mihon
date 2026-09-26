package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.library.DeleteLibraryMangaDialog
import eu.kanade.presentation.library.LibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.manga.components.LibraryBottomActionMenu
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.ui.home.ShowBottomNavEvent
import eu.kanade.tachiyomi.ui.home.TabReselectEventKey
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.launch
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.GlobalSearchRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.MigrationConfigRoute
import mihon.core.navigation.util.LocalBackStack
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Help
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.isLocal

@Composable
fun LibraryTab() {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val resultBus = LocalResultEventBus.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val viewModel = metroViewModel<LibraryViewModel>()
    val settingsViewModel = metroViewModel<LibrarySettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val onClickRefresh: (Category?) -> Boolean = { category ->
        val started = LibraryUpdateJob.startNow(context.workManager, category)
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
            val title = state.getToolbarTitle(
                defaultTitle = stringResource(MR.strings.label_library),
                defaultCategoryTitle = stringResource(MR.strings.label_default),
                page = state.coercedActiveCategoryIndex,
            )
            LibraryToolbar(
                hasActiveFilters = state.hasActiveFilters,
                selectedCount = state.selection.size,
                title = title,
                onClickUnselectAll = viewModel::clearSelection,
                onClickSelectAll = viewModel::selectAll,
                onClickInvertSelection = viewModel::invertSelection,
                onClickFilter = viewModel::showSettingsDialog,
                onClickRefresh = { onClickRefresh(state.activeCategory) },
                onClickGlobalUpdate = { onClickRefresh(null) },
                onClickOpenRandomManga = {
                    scope.launch {
                        val randomItem = viewModel.getRandomLibraryItemForCurrentCategory()
                        if (randomItem != null) {
                            backStack.add(MangaRoute(randomItem.libraryManga.manga.id))
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
                scrollBehavior = scrollBehavior.takeIf { !state.showCategoryTabs },
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
                    backStack.add(MigrationConfigRoute(selection))
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
            else -> {
                LibraryContent(
                    categories = state.displayedCategories,
                    searchQuery = state.searchQuery,
                    selection = state.selection,
                    contentPadding = contentPadding,
                    currentPage = state.coercedActiveCategoryIndex,
                    hasActiveFilters = state.hasActiveFilters,
                    showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                    onChangeCurrentPage = viewModel::updateActiveCategoryIndex,
                    onClickManga = { backStack.add(MangaRoute(it)) },
                    onContinueReadingClicked = { it: LibraryManga ->
                        scope.launchIO {
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
                        backStack.add(GlobalSearchRoute(viewModel.state.value.searchQuery ?: ""))
                    },
                    getItemCountForCategory = { state.getItemCountForCategory(it) },
                    getDisplayMode = { viewModel.getDisplayMode() },
                    getColumnsForOrientation = { viewModel.getColumnsForOrientation(it) },
                    getItemsForCategory = { state.getItemsForCategory(it) },
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
                    backStack.add(CategoryRoute)
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

    LaunchedEffect(state.selectionMode, state.dialog) {
        resultBus.sendResult(
            result = ShowBottomNavEvent(!state.selectionMode),
        )
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            (context as? MainActivity)?.ready = true
        }
    }

    ResultEffect<String>(resultKey = LibraryTabSearchEventKey) {
        viewModel.search(it)
    }

    ResultEffect<Unit>(resultKey = TabReselectEventKey) {
        viewModel.showSettingsDialog()
    }
}

@Suppress("ConstPropertyName")
const val LibraryTabSearchEventKey = "LibraryTabSearchEventKey"
