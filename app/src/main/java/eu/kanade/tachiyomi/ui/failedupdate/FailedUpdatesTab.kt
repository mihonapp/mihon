package eu.kanade.tachiyomi.ui.failedupdate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.failedupdate.FailedUpdatesClearAllDialog
import eu.kanade.presentation.failedupdate.FailedUpdatesScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.config.MigrationConfigScreen

data object FailedUpdatesTab : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FailedUpdatesScreenModel() }
        val state by screenModel.state.collectAsState()

        // Clean up non-favorite errors when returning to this screen
        LaunchedEffect(Unit) {
            screenModel.cleanupNonFavorites()
        }

        FailedUpdatesScreen(
            state = state,
            onClickCover = { item -> navigator.push(MangaScreen(item.manga.id)) },
            onClickMigrate = {
                val selectedIds = screenModel.getSelectedMangaIds()
                if (selectedIds.isNotEmpty()) {
                    screenModel.setDialog(FailedUpdatesScreenModel.Dialog.MigrateSelected(selectedIds))
                }
            },
            onClearAll = {
                screenModel.setDialog(FailedUpdatesScreenModel.Dialog.ClearAllConfirmation)
            },
            onClearError = { mangaId ->
                screenModel.clearError(mangaId)
            },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onToggleSelection = screenModel::toggleSelection,
            navigateUp = navigator::pop,
        )

        val onDismissDialog = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is FailedUpdatesScreenModel.Dialog.ClearAllConfirmation -> {
                FailedUpdatesClearAllDialog(
                    onDismissRequest = onDismissDialog,
                    onConfirm = {
                        screenModel.clearAllErrors()
                    },
                )
            }
            is FailedUpdatesScreenModel.Dialog.MigrateSelected -> {
                // Navigate to migration config screen to select sources, then to migration list
                navigator.push(MigrationConfigScreen(dialog.mangaIds))
                onDismissDialog()
            }
            null -> {}
        }
    }
}
