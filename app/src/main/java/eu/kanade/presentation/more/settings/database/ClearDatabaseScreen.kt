package eu.kanade.presentation.more.settings.database

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseContent
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseDeleteDialog
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseFloatingActionButton
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseToolbar
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabasePresenter
import eu.kanade.tachiyomi.util.system.toast

@Composable
fun ClearDatabaseScreen(
    presenter: ClearDatabasePresenter,
    navigateUp: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            ClearDatabaseToolbar(
                state = presenter,
                navigateUp = navigateUp,
                onClickSelectAll = { presenter.selectAll() },
                onClickInvertSelection = { presenter.invertSelection() },
            )
        },
        floatingActionButton = {
            ClearDatabaseFloatingActionButton(
                isVisible = presenter.selection.isNotEmpty(),
                onClickDelete = {
                    presenter.dialog = ClearDatabasePresenter.Dialog.Delete(presenter.selection)
                },
            )
        },
    ) { paddingValues ->
        ClearDatabaseContent(
            state = presenter,
            contentPadding = paddingValues,
            onClickSelection = { source ->
                presenter.toggleSelection(source)
            },
        )
    }
    val dialog = presenter.dialog
    if (dialog is ClearDatabasePresenter.Dialog.Delete) {
        ClearDatabaseDeleteDialog(
            onDismissRequest = { presenter.dialog = null },
            onDelete = {
                presenter.removeMangaBySourceId(dialog.sourceIds)
                presenter.clearSelection()
                presenter.dialog = null
                context.toast(R.string.clear_database_completed)
            },
        )
    }
}
