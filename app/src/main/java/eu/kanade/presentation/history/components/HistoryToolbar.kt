package eu.kanade.presentation.history.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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
            placeholderText = stringResource(R.string.action_search_hint),
            onClickCloseSearch = { state.searchQuery = null },
            onClickResetSearch = { state.searchQuery = "" },
            incognitoMode = incognitoMode,
            downloadedOnlyMode = downloadedOnlyMode,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
            ),
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
