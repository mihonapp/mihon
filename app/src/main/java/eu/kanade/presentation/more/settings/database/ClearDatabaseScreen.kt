package eu.kanade.presentation.more.settings.database

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseContent
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseDeleteDialog
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseFloatingActionButton
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabasePresenter
import eu.kanade.tachiyomi.util.system.toast

@Composable
fun ClearDatabaseScreen(
    presenter: ClearDatabasePresenter,
    navigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            ClearDatabaseToolbar(
                state = presenter,
                navigateUp = navigateUp,
                onClickSelectAll = { presenter.selectAll() },
                onClickInvertSelection = { presenter.invertSelection() },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ClearDatabaseFloatingActionButton(
                isVisible = presenter.selection.isNotEmpty(),
                lazyListState = lazyListState,
                onClickDelete = {
                    presenter.dialog = ClearDatabasePresenter.Dialog.Delete(presenter.selection)
                },
            )
        },
    ) { paddingValues ->
        ClearDatabaseContent(
            state = presenter,
            contentPadding = paddingValues,
            lazyListState = lazyListState,
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
