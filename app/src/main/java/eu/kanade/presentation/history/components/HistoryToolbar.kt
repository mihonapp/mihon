package eu.kanade.presentation.history.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.recent.history.HistoryPresenter
import eu.kanade.tachiyomi.ui.recent.history.HistoryState

@Composable
fun HistoryToolbar(
    state: HistoryState,
    scrollBehavior: TopAppBarScrollBehavior,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
) {
    if (state.searchQuery == null) {
        HistoryRegularToolbar(
            onClickSearch = { state.searchQuery = "" },
            onClickDelete = { state.dialog = HistoryPresenter.Dialog.DeleteAll },
            incognitoMode = incognitoMode,
            downloadedOnlyMode = downloadedOnlyMode,
            scrollBehavior = scrollBehavior,
        )
    } else {
        SearchToolbar(
            searchQuery = state.searchQuery!!,
            onChangeSearchQuery = { state.searchQuery = it },
            onClickCloseSearch = { state.searchQuery = null },
            onClickResetSearch = { state.searchQuery = "" },
            incognitoMode = incognitoMode,
            downloadedOnlyMode = downloadedOnlyMode,
        )
    }
}

@Composable
fun HistoryRegularToolbar(
    onClickSearch: () -> Unit,
    onClickDelete: () -> Unit,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        title = stringResource(R.string.history),
        actions = {
            IconButton(onClick = onClickSearch) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
            }
            IconButton(onClick = onClickDelete) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.pref_clear_history))
            }
        },
        downloadedOnlyMode = downloadedOnlyMode,
        incognitoMode = incognitoMode,
        scrollBehavior = scrollBehavior,
    )
}
