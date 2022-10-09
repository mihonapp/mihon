package eu.kanade.presentation.history

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
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
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView
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
        topBar = { scrollBehavior ->
            HistoryToolbar(
                state = presenter,
                incognitoMode = presenter.isIncognitoMode,
                downloadedOnlyMode = presenter.isDownloadOnly,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val items by presenter.getHistory().collectAsState(initial = null)
        val contentPaddingWithNavBar = TachiyomiBottomNavigationView.withBottomNavPadding(contentPadding)
        items.let {
            if (it == null) {
                LoadingScreen()
            } else if (it.isEmpty()) {
                EmptyScreen(
                    textResource = R.string.information_no_recent_manga,
                    modifier = Modifier.padding(contentPaddingWithNavBar),
                )
            } else {
                HistoryContent(
                    history = it,
                    contentPadding = contentPaddingWithNavBar,
                    onClickCover = onClickCover,
                    onClickResume = onClickResume,
                    onClickDelete = { item -> presenter.dialog = Dialog.Delete(item) },
                )
            }
        }

        LaunchedEffect(items) {
            if (items != null) {
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
