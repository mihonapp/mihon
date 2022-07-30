package eu.kanade.presentation.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.MangaBottomActionMenu
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.SwipeRefreshIndicator
import eu.kanade.presentation.components.VerticalFastScroller
import eu.kanade.presentation.util.bottomNavPaddingValues
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesPresenter
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesPresenter.Dialog
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesPresenter.Event
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import java.util.Date

@Composable
fun UpdateScreen(
    presenter: UpdatesPresenter,
    onClickCover: (UpdatesItem) -> Unit,
    onBackClicked: () -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
) {
    val updatesListState = rememberLazyListState()
    val insetPaddingValue = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()

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
        if (LibraryUpdateService.start(context)) {
            context.toast(R.string.updating_library)
        }
    }

    Scaffold(
        modifier = Modifier
            .padding(insetPaddingValue),
        topBar = {
            UpdatesAppBar(
                incognitoMode = presenter.isIncognitoMode,
                downloadedOnlyMode = presenter.isDownloadOnly,
                onUpdateLibrary = onUpdateLibrary,
                actionModeCounter = presenter.selected.size,
                onSelectAll = { presenter.toggleAllSelection(true) },
                onInvertSelection = { presenter.invertSelection() },
                onCancelActionMode = { presenter.toggleAllSelection(false) },
            )
        },
        bottomBar = {
            UpdatesBottomBar(
                selected = presenter.selected,
                onDownloadChapter = onDownloadChapter,
                onMultiBookmarkClicked = presenter::bookmarkUpdates,
                onMultiMarkAsReadClicked = presenter::markUpdatesRead,
                onMultiDeleteClicked = {
                    val updateItems = presenter.selected.map { it.item }
                    presenter.dialog = Dialog.DeleteConfirmation(updateItems)
                },
            )
        },
    ) { contentPadding ->
        // During selection mode bottom nav is not visible
        val contentPaddingWithNavBar = (if (presenter.selectionMode) PaddingValues() else bottomNavPaddingValues) +
            contentPadding + WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()

        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = false),
            onRefresh = onUpdateLibrary,
            swipeEnabled = presenter.selectionMode.not(),
            indicatorPadding = contentPaddingWithNavBar,
            indicator = { s, trigger ->
                SwipeRefreshIndicator(
                    state = s,
                    refreshTriggerDistance = trigger,
                )
            },
        ) {
            if (presenter.uiModels.isEmpty()) {
                EmptyScreen(textResource = R.string.information_no_recent)
            } else {
                VerticalFastScroller(
                    listState = updatesListState,
                    topContentPadding = contentPaddingWithNavBar.calculateTopPadding(),
                    bottomContentPadding = contentPaddingWithNavBar.calculateBottomPadding(),
                    endContentPadding = contentPaddingWithNavBar.calculateEndPadding(LocalLayoutDirection.current),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        state = updatesListState,
                        contentPadding = contentPaddingWithNavBar,
                    ) {
                        updatesUiItems(
                            uiModels = presenter.uiModels,
                            selectionMode = presenter.selectionMode,
                            onUpdateSelected = presenter::toggleSelection,
                            onClickCover = onClickCover,
                            onClickUpdate = {
                                val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
                                context.startActivity(intent)
                            },
                            onDownloadChapter = onDownloadChapter,
                            relativeTime = presenter.relativeTime,
                            dateFormat = presenter.dateFormat,
                        )
                    }
                }
            }
        }
    }

    val onDismissDialog = { presenter.dialog = null }
    when (val dialog = presenter.dialog) {
        is Dialog.DeleteConfirmation -> {
            UpdatesDeleteConfirmationDialog(
                onDismissRequest = onDismissDialog,
                onConfirm = {
                    presenter.deleteChapters(dialog.toDelete)
                    presenter.toggleAllSelection(false)
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
fun UpdatesAppBar(
    modifier: Modifier = Modifier,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    onUpdateLibrary: () -> Unit,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
) {
    AppBar(
        modifier = modifier,
        title = stringResource(R.string.label_recent_updates),
        actions = {
            IconButton(onClick = onUpdateLibrary) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_update_library),
                )
            }
        },
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.action_select_all),
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Default.FlipToBack,
                    contentDescription = stringResource(R.string.action_select_inverse),
                )
            }
        },
        downloadedOnlyMode = downloadedOnlyMode,
        incognitoMode = incognitoMode,
    )
}

@Composable
fun UpdatesBottomBar(
    selected: List<UpdatesUiModel.Item>,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.map { it.item }, true)
        }.takeIf { selected.any { !it.item.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.map { it.item }, false)
        }.takeIf { selected.all { it.item.update.bookmark } },
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected.map { it.item }, true)
        }.takeIf { selected.any { !it.item.update.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected.map { it.item }, false)
        }.takeIf { selected.any { it.item.update.read } },
        onDownloadClicked = {
            onDownloadChapter(selected.map { it.item }, ChapterDownloadAction.START)
        }.takeIf {
            selected.any { it.item.downloadStateProvider() != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.map { it.item })
        }.takeIf { selected.any { it.item.downloadStateProvider() == Download.State.DOWNLOADED } },
    )
}

sealed class UpdatesUiModel {
    data class Header(val date: Date) : UpdatesUiModel()
    data class Item(val item: UpdatesItem) : UpdatesUiModel()
}
