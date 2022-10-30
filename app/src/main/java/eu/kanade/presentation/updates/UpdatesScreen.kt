package eu.kanade.presentation.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.MangaBottomActionMenu
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.SwipeRefresh
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesPresenter
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesPresenter.Dialog
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesPresenter.Event
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.time.Duration.Companion.seconds

@Composable
fun UpdateScreen(
    presenter: UpdatesPresenter,
    onClickCover: (UpdatesItem) -> Unit,
    onBackClicked: () -> Unit,
) {
    val internalOnBackPressed = {
        if (presenter.selectionMode) {
            presenter.toggleAllSelection(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

    val context = LocalContext.current
    val onUpdateLibrary = {
        val started = LibraryUpdateService.start(context)
        context.toast(if (started) R.string.updating_library else R.string.update_already_running)
        started
    }

    Scaffold(
        topBar = { scrollBehavior ->
            UpdatesAppBar(
                incognitoMode = presenter.isIncognitoMode,
                downloadedOnlyMode = presenter.isDownloadOnly,
                onUpdateLibrary = { onUpdateLibrary() },
                actionModeCounter = presenter.selected.size,
                onSelectAll = { presenter.toggleAllSelection(true) },
                onInvertSelection = { presenter.invertSelection() },
                onCancelActionMode = { presenter.toggleAllSelection(false) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            UpdatesBottomBar(
                selected = presenter.selected,
                onDownloadChapter = presenter::downloadChapters,
                onMultiBookmarkClicked = presenter::bookmarkUpdates,
                onMultiMarkAsReadClicked = presenter::markUpdatesRead,
                onMultiDeleteClicked = {
                    presenter.dialog = Dialog.DeleteConfirmation(it)
                },
            )
        },
    ) { contentPadding ->
        val contentPaddingWithNavBar = TachiyomiBottomNavigationView.withBottomNavPadding(contentPadding)
        when {
            presenter.isLoading -> LoadingScreen()
            presenter.uiModels.isEmpty() -> EmptyScreen(
                textResource = R.string.information_no_recent,
                modifier = Modifier.padding(contentPaddingWithNavBar),
            )
            else -> {
                UpdateScreenContent(
                    presenter = presenter,
                    contentPadding = contentPaddingWithNavBar,
                    onUpdateLibrary = onUpdateLibrary,
                    onClickCover = onClickCover,
                )
            }
        }
    }
}

@Composable
private fun UpdateScreenContent(
    presenter: UpdatesPresenter,
    contentPadding: PaddingValues,
    onUpdateLibrary: () -> Boolean,
    onClickCover: (UpdatesItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    SwipeRefresh(
        refreshing = isRefreshing,
        onRefresh = {
            val started = onUpdateLibrary()
            if (!started) return@SwipeRefresh
            scope.launch {
                // Fake refresh status but hide it after a second as it's a long running task
                isRefreshing = true
                delay(1.seconds)
                isRefreshing = false
            }
        },
        enabled = presenter.selectionMode.not(),
        indicatorPadding = contentPadding,
    ) {
        FastScrollLazyColumn(
            contentPadding = contentPadding,
        ) {
            if (presenter.lastUpdated > 0L) {
                updatesLastUpdatedItem(presenter.lastUpdated)
            }

            updatesUiItems(
                uiModels = presenter.uiModels,
                selectionMode = presenter.selectionMode,
                onUpdateSelected = presenter::toggleSelection,
                onClickCover = onClickCover,
                onClickUpdate = {
                    val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
                    context.startActivity(intent)
                },
                onDownloadChapter = presenter::downloadChapters,
                relativeTime = presenter.relativeTime,
                dateFormat = presenter.dateFormat,
            )
        }
    }

    val onDismissDialog = { presenter.dialog = null }
    when (val dialog = presenter.dialog) {
        is Dialog.DeleteConfirmation -> {
            UpdatesDeleteConfirmationDialog(
                onDismissRequest = onDismissDialog,
                onConfirm = {
                    presenter.toggleAllSelection(false)
                    presenter.deleteChapters(dialog.toDelete)
                },
            )
        }
        null -> {}
    }
    LaunchedEffect(Unit) {
        presenter.events.collectLatest { event ->
            when (event) {
                Event.InternalError -> context.toast(R.string.internal_error)
            }
        }
    }
}

@Composable
private fun UpdatesAppBar(
    modifier: Modifier = Modifier,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    onUpdateLibrary: () -> Unit,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        modifier = modifier,
        title = stringResource(R.string.label_recent_updates),
        actions = {
            IconButton(onClick = onUpdateLibrary) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.action_update_library),
                )
            }
        },
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(R.string.action_select_all),
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Outlined.FlipToBack,
                    contentDescription = stringResource(R.string.action_select_inverse),
                )
            }
        },
        downloadedOnlyMode = downloadedOnlyMode,
        incognitoMode = incognitoMode,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun UpdatesBottomBar(
    selected: List<UpdatesItem>,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, true)
        }.takeIf { selected.fastAny { !it.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, false)
        }.takeIf { selected.fastAll { it.update.bookmark } },
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected, true)
        }.takeIf { selected.fastAny { !it.update.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected, false)
        }.takeIf { selected.fastAny { it.update.read } },
        onDownloadClicked = {
            onDownloadChapter(selected, ChapterDownloadAction.START)
        }.takeIf {
            selected.fastAny { it.downloadStateProvider() != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected)
        }.takeIf { selected.fastAny { it.downloadStateProvider() == Download.State.DOWNLOADED } },
    )
}

sealed class UpdatesUiModel {
    data class Header(val date: Date) : UpdatesUiModel()
    data class Item(val item: UpdatesItem) : UpdatesUiModel()
}
