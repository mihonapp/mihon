package eu.kanade.presentation.history.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBarTitle
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
    SearchToolbar(
        titleContent = { AppBarTitle(stringResource(R.string.history)) },
        searchQuery = state.searchQuery,
        onChangeSearchQuery = { state.searchQuery = it },
        actions = {
            IconButton(onClick = { state.dialog = HistoryPresenter.Dialog.DeleteAll }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.pref_clear_history))
            }
        },
        downloadedOnlyMode = downloadedOnlyMode,
        incognitoMode = incognitoMode,
        scrollBehavior = scrollBehavior,
    )
}
