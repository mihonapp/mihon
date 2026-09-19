package eu.kanade.tachiyomi.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import cafe.adriel.voyager.navigator.Navigator
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.tachiyomi.ui.download.DownloadQueueRoute
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.ShowBottomNavEvent
import eu.kanade.tachiyomi.ui.home.TabReselectEventKey
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaRoute
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesViewModel.Event
import kotlinx.coroutines.flow.collectLatest
import mihon.feature.upcoming.UpcomingRoute
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

@Composable
fun UpdatesTab() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val resultBus = LocalResultEventBus.current
    val viewModel = metroViewModel<UpdatesViewModel>()
    val settingsViewModel = metroViewModel<UpdatesSettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    UpdateScreen(
        state = state,
        snackbarHostState = viewModel.snackbarHostState,
        lastUpdated = viewModel.lastUpdated,
        onClickCover = { item -> backStack.add(MangaRoute(item.update.mangaId)) },
        onSelectAll = viewModel::toggleAllSelection,
        onInvertSelection = viewModel::invertSelection,
        onUpdateLibrary = viewModel::updateLibrary,
        onDownloadChapter = viewModel::downloadChapters,
        onMultiBookmarkClicked = viewModel::bookmarkUpdates,
        onMultiMarkAsReadClicked = viewModel::markUpdatesRead,
        onMultiDeleteClicked = viewModel::showConfirmDeleteChapters,
        onUpdateSelected = viewModel::toggleSelection,
        onOpenChapter = {
            val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
            context.startActivity(intent)
        },
        onCalendarClicked = { backStack.add(UpcomingRoute) },
        onFilterClicked = viewModel::showFilterDialog,
        hasActiveFilters = state.hasActiveFilters,
    )

    val onDismissDialog = { viewModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is UpdatesViewModel.Dialog.DeleteConfirmation -> {
            UpdatesDeleteConfirmationDialog(
                onDismissRequest = onDismissDialog,
                onConfirm = { viewModel.deleteChapters(dialog.toDelete) },
            )
        }
        is UpdatesViewModel.Dialog.FilterSheet -> {
            UpdatesFilterDialog(
                onDismissRequest = onDismissDialog,
                viewModel = settingsViewModel,
            )
        }
        null -> {}
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                Event.InternalError -> viewModel.snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.internal_error),
                )
                is Event.LibraryUpdateTriggered -> {
                    val msg = if (event.started) {
                        MR.strings.updating_library
                    } else {
                        MR.strings.update_already_running
                    }
                    viewModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                }
            }
        }
    }

    LaunchedEffect(state.selectionMode) {
        resultBus.sendResult(
            result = ShowBottomNavEvent(!state.selectionMode)
        )
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            (context as? MainActivity)?.ready = true
        }
    }

    ResultEffect<Unit>(resultKey = TabReselectEventKey) {
        backStack.add(DownloadQueueRoute)
    }

    DisposableEffect(Unit) {
        viewModel.resetNewUpdatesCount()

        onDispose {
            viewModel.resetNewUpdatesCount()
        }
    }
}

data object UpdatesTab {
    suspend fun onReselect(navigator: Navigator) {
        // navigator.push(DownloadQueueScreen)
    }
}
