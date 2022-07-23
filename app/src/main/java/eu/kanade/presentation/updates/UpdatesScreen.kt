package eu.kanade.presentation.updates

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
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
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Date

@Composable
fun UpdateScreen(
    state: UpdatesState.Success,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onUpdateLibrary: () -> Unit,
    onBackClicked: () -> Unit,
    // For bottom action menu
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
    // Miscellaneous
    preferences: PreferencesHelper = Injekt.get(),
) {
    val updatesListState = rememberLazyListState()
    val insetPaddingValue = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()

    val relativeTime: Int = remember { preferences.relativeTime().get() }
    val dateFormat: DateFormat = remember { preferences.dateFormat() }

    val uiModels = remember(state) {
        state.uiModels
    }
    val itemUiModels = remember(uiModels) {
        uiModels.filterIsInstance<UpdatesUiModel.Item>()
    }
    // To prevent selection from getting removed during an update to a item in list
    val updateIdList = remember(itemUiModels) {
        itemUiModels.map { it.item.update.chapterId }
    }
    val selected = remember(updateIdList) {
        emptyList<UpdatesUiModel.Item>().toMutableStateList()
    }
    // First and last selected index in list
    val selectedPositions = remember(uiModels) { arrayOf(-1, -1) }

    val internalOnBackPressed = {
        if (selected.isNotEmpty()) {
            selected.clear()
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

    Scaffold(
        modifier = Modifier
            .padding(insetPaddingValue),
        topBar = {
            UpdatesAppBar(
                selected = selected,
                incognitoMode = state.isIncognitoMode,
                downloadedOnlyMode = state.isDownloadedOnlyMode,
                onUpdateLibrary = onUpdateLibrary,
                actionModeCounter = selected.size,
                onSelectAll = {
                    selected.clear()
                    selected.addAll(itemUiModels)
                },
                onInvertSelection = {
                    val toSelect = itemUiModels - selected
                    selected.clear()
                    selected.addAll(toSelect)
                },
            )
        },
        bottomBar = {
            UpdatesBottomBar(
                selected = selected,
                onDownloadChapter = onDownloadChapter,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMultiDeleteClicked = onMultiDeleteClicked,
            )
        },
    ) { contentPadding ->
        val contentPaddingWithNavBar = bottomNavPaddingValues + contentPadding +
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()

        SwipeRefresh(
            state = rememberSwipeRefreshState(state.showSwipeRefreshIndicator),
            onRefresh = onUpdateLibrary,
            indicatorPadding = contentPaddingWithNavBar,
            indicator = { s, trigger ->
                SwipeRefreshIndicator(
                    state = s,
                    refreshTriggerDistance = trigger,
                )
            },
        ) {
            if (uiModels.isEmpty()) {
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
                            uiModels = uiModels,
                            itemUiModels = itemUiModels,
                            selected = selected,
                            selectedPositions = selectedPositions,
                            onClickCover = onClickCover,
                            onClickUpdate = onClickUpdate,
                            onDownloadChapter = onDownloadChapter,
                            relativeTime = relativeTime,
                            dateFormat = dateFormat,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdatesAppBar(
    modifier: Modifier = Modifier,
    selected: MutableList<UpdatesUiModel.Item>,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    onUpdateLibrary: () -> Unit,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
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
        onCancelActionMode = { selected.clear() },
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
    selected: MutableList<UpdatesUiModel.Item>,
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
            selected.clear()
        }.takeIf { selected.any { !it.item.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.map { it.item }, false)
            selected.clear()
        }.takeIf { selected.all { it.item.update.bookmark } },
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected.map { it.item }, true)
            selected.clear()
        }.takeIf { selected.any { !it.item.update.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected.map { it.item }, false)
            selected.clear()
        }.takeIf { selected.any { it.item.update.read } },
        onDownloadClicked = {
            onDownloadChapter(selected.map { it.item }, ChapterDownloadAction.START)
            selected.clear()
        }.takeIf {
            selected.any { it.item.downloadStateProvider() != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.map { it.item })
            selected.clear()
        }.takeIf { selected.any { it.item.downloadStateProvider() == Download.State.DOWNLOADED } },
    )
}

sealed class UpdatesUiModel {
    data class Header(val date: Date) : UpdatesUiModel()
    data class Item(val item: UpdatesItem) : UpdatesUiModel()
}
