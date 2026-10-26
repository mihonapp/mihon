package eu.kanade.tachiyomi.ui.failedupdate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.failedupdate.FailedUpdatesClearAllDialog
import eu.kanade.presentation.failedupdate.FailedUpdatesDeleteSelectedDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.config.MigrationConfigScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import eu.kanade.presentation.failedupdate.FailedUpdatesScreen as FailedUpdatesScreenContent

object FailedUpdatesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<FailedUpdatesViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.events.collect {
                viewModel.snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
            }
        }

        FailedUpdatesScreenContent(
            state = state,
            snackbarHostState = viewModel.snackbarHostState,
            onClickCover = { item -> navigator.push(MangaScreen(item.manga.id)) },
            onClickItem = { item ->
                navigator.push(MigrationConfigScreen(item.manga.id))
            },
            onClickMigrate = {
                val selectedIds = viewModel.state.value.selectedIds.toList()
                if (selectedIds.isNotEmpty()) {
                    navigator.push(MigrationConfigScreen(selectedIds))
                }
            },
            onClearAll = {
                viewModel.setDialog(FailedUpdatesViewModel.Dialog.ClearAllConfirmation)
            },
            onDeleteSelected = {
                viewModel.setDialog(FailedUpdatesViewModel.Dialog.DeleteSelectedConfirmation)
            },
            onClearError = { mangaId ->
                viewModel.clearError(mangaId)
            },
            onSelectAll = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            onToggleSelection = viewModel::toggleSelection,
            navigateUp = navigator::pop,
        )

        val onDismissDialog = { viewModel.setDialog(null) }
        when (state.dialog) {
            is FailedUpdatesViewModel.Dialog.ClearAllConfirmation -> {
                FailedUpdatesClearAllDialog(
                    onDismissRequest = onDismissDialog,
                    onConfirm = {
                        viewModel.clearAllErrors()
                    },
                )
            }
            is FailedUpdatesViewModel.Dialog.DeleteSelectedConfirmation -> {
                FailedUpdatesDeleteSelectedDialog(
                    onDismissRequest = onDismissDialog,
                    onConfirm = {
                        viewModel.clearSelectedErrors()
                    },
                )
            }
            null -> {}
        }
    }
}
