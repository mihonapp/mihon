package eu.kanade.tachiyomi.ui.history

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.category.CategoryRoute
import eu.kanade.tachiyomi.ui.home.TabReselectEventKey
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaRoute
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.navigation.util.LocalBackStack
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR

@Composable
fun HistoryTab() {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel = metroViewModel<HistoryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    HistoryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onClickCover = { backStack.add(MangaRoute(it)) },
        onClickResume = viewModel::getNextChapterForManga,
        onDialogChange = viewModel::setDialog,
        onClickFavorite = viewModel::addFavorite,
    )

    val onDismissRequest = { viewModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is HistoryViewModel.Dialog.Delete -> {
            HistoryDeleteDialog(
                onDismissRequest = onDismissRequest,
                onDelete = { all ->
                    if (all) {
                        viewModel.removeAllFromHistory(dialog.history.mangaId)
                    } else {
                        viewModel.removeFromHistory(dialog.history)
                    }
                },
            )
        }
        is HistoryViewModel.Dialog.DeleteAll -> {
            HistoryDeleteAllDialog(
                onDismissRequest = onDismissRequest,
                onDelete = viewModel::removeAllHistory,
            )
        }
        is HistoryViewModel.Dialog.DuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { viewModel.addFavorite(dialog.manga) },
                onOpenManga = { backStack.add(MangaRoute(it.id)) },
                onMigrate = { viewModel.showMigrateDialog(dialog.manga, it) },
            )
        }
        is HistoryViewModel.Dialog.ChangeCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = { backStack.add(CategoryRoute) },
                onConfirm = { include, _ ->
                    viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                },
            )
        }
        is HistoryViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                // Initiated from the context of [dialog.target] so we show [dialog.current].
                onClickTitle = { backStack.add(MangaRoute(dialog.current.id)) },
                onDismissRequest = onDismissRequest,
            )
        }
        null -> {}
    }

    LaunchedEffect(state.list) {
        if (state.list != null) {
            (context as? MainActivity)?.ready = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { e ->
            when (e) {
                HistoryViewModel.Event.InternalError ->
                    snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                HistoryViewModel.Event.HistoryCleared ->
                    snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                is HistoryViewModel.Event.OpenChapter -> openChapter(context, snackbarHostState, e.chapter)
            }
        }
    }

    ResultEffect<Unit>(resultKey = TabReselectEventKey) {
        openChapter(context, snackbarHostState, viewModel.getNextChapter())
    }
}

private suspend fun openChapter(context: Context, snackbarHostState: SnackbarHostState, chapter: Chapter?) {
    if (chapter != null) {
        val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
        context.startActivity(intent)
    } else {
        snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
    }
}
