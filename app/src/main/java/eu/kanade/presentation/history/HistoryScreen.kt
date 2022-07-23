package eu.kanade.presentation.history

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.LoadState
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.history.components.HistoryContent
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.history.components.HistoryToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recent.history.HistoryPresenter
import eu.kanade.tachiyomi.ui.recent.history.HistoryPresenter.Dialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import java.util.Date

@Composable
fun HistoryScreen(
    presenter: HistoryPresenter,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            HistoryToolbar(state = presenter)
        },
    ) { contentPadding ->
        val items = presenter.getLazyHistory()
        when {
            items.loadState.refresh is LoadState.Loading && items.itemCount < 1 -> LoadingScreen()
            items.loadState.refresh is LoadState.NotLoading && items.itemCount < 1 -> EmptyScreen(textResource = R.string.information_no_recent_manga)
            else -> HistoryContent(
                history = items,
                contentPadding = contentPadding,
                onClickCover = onClickCover,
                onClickResume = onClickResume,
                onClickDelete = { presenter.dialog = Dialog.Delete(it) },
            )
        }
        LaunchedEffect(items.loadState.refresh) {
            if (items.loadState.refresh is LoadState.NotLoading) {
                (presenter.view?.activity as? MainActivity)?.ready = true
            }
        }
    }
    val onDismissRequest = { presenter.dialog = null }
    when (val dialog = presenter.dialog) {
        is Dialog.Delete -> {
            HistoryDeleteDialog(
                onDismissRequest = onDismissRequest,
                onDelete = { all ->
                    if (all) {
                        presenter.removeAllFromHistory(dialog.history.mangaId)
                    } else {
                        presenter.removeFromHistory(dialog.history)
                    }
                },
            )
        }
        is Dialog.DeleteAll -> {
            HistoryDeleteAllDialog(
                onDismissRequest = onDismissRequest,
                onDelete = {
                    presenter.deleteAllHistory()
                },
            )
        }
        null -> {}
    }
    LaunchedEffect(Unit) {
        presenter.events.collectLatest { event ->
            when (event) {
                HistoryPresenter.Event.InternalError -> context.toast(R.string.internal_error)
                HistoryPresenter.Event.NoNextChapterFound -> context.toast(R.string.no_next_chapter)
                is HistoryPresenter.Event.OpenChapter -> {
                    val intent = ReaderActivity.newIntent(context, event.chapter.mangaId, event.chapter.id)
                    context.startActivity(intent)
                }
            }
        }
    }
}

sealed class HistoryUiModel {
    data class Header(val date: Date) : HistoryUiModel()
    data class Item(val item: HistoryWithRelations) : HistoryUiModel()
}
