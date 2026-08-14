package eu.kanade.tachiyomi.ui.failedupdate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.failedupdate.FailedUpdatesClearAllDialog
import eu.kanade.presentation.failedupdate.FailedUpdatesDeleteSelectedDialog
import eu.kanade.presentation.failedupdate.FailedUpdatesScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.config.MigrationConfigScreen

data object FailedUpdatesTab : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<FailedUpdatesViewModel>()
        val state by viewModel.state.collectAsState()

        // Clean up non-favorite errors when screen is opened
        LaunchedEffect(Unit) {
            viewModel.cleanupNonFavorites()
        }

        FailedUpdatesScreen(
            state = state,
            onClickCover = { item -> navigator.push(MangaScreen(item.manga.id)) },
            onClickItem = { item ->
                // Navigate directly to migration for single manga
                navigator.push(MigrationConfigScreen(item.manga.id))
            },
            onClickMigrate = {
                val selectedIds = viewModel.getSelectedMangaIds()
                if (selectedIds.isNotEmpty()) {
                    viewModel.setDialog(FailedUpdatesViewModel.Dialog.MigrateSelected(selectedIds))
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
        when (val dialog = state.dialog) {
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
            is FailedUpdatesViewModel.Dialog.MigrateSelected -> {
                // Navigate to migration config screen to select sources, then to migration list
                navigator.push(MigrationConfigScreen(dialog.mangaIds))
                onDismissDialog()
            }
            null -> {}
        }
    }
}
