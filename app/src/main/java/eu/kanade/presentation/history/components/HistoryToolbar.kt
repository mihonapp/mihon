package eu.kanade.presentation.history.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.recent.history.HistoryPresenter
import eu.kanade.tachiyomi.ui.recent.history.HistoryState
import kotlinx.coroutines.delay

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
        HistorySearchToolbar(
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
        title = stringResource(id = R.string.history),
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

@Composable
fun HistorySearchToolbar(
    searchQuery: String,
    onChangeSearchQuery: (String) -> Unit,
    onClickCloseSearch: () -> Unit,
    onClickResetSearch: () -> Unit,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
) {
    val focusRequester = remember { FocusRequester.Default }
    AppBar(
        titleContent = {
            BasicTextField(
                value = searchQuery,
                onValueChange = onChangeSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            )
        },
        navigationIcon = Icons.Outlined.ArrowBack,
        navigateUp = onClickCloseSearch,
        actions = {
            AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                IconButton(onClick = onClickResetSearch) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(id = R.string.action_reset))
                }
            }
        },
        isActionMode = false,
        downloadedOnlyMode = downloadedOnlyMode,
        incognitoMode = incognitoMode,
    )
    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(100)
        focusRequester.requestFocus()
    }
}
