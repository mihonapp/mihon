package eu.kanade.presentation.history.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.recent.history.HistoryPresenter
import eu.kanade.tachiyomi.ui.recent.history.HistoryState
import kotlinx.coroutines.delay

@Composable
fun HistoryToolbar(
    state: HistoryState,
) {
    if (state.searchQuery == null) {
        HistoryRegularToolbar(
            onClickSearch = { state.searchQuery = "" },
            onClickDelete = { state.dialog = HistoryPresenter.Dialog.DeleteAll },
        )
    } else {
        HistorySearchToolbar(
            searchQuery = state.searchQuery!!,
            onChangeSearchQuery = { state.searchQuery = it },
            onClickCloseSearch = { state.searchQuery = null },
        )
    }
}

@Composable
fun HistoryRegularToolbar(
    onClickSearch: () -> Unit,
    onClickDelete: () -> Unit,
) {
    SmallTopAppBar(
        title = {
            Text(text = stringResource(id = R.string.history))
        },
        actions = {
            IconButton(onClick = onClickSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "search")
            }
            IconButton(onClick = onClickDelete) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "delete")
            }
        },
    )
}

@Composable
fun HistorySearchToolbar(
    searchQuery: String,
    onChangeSearchQuery: (String) -> Unit,
    onClickCloseSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester.Default }
    SmallTopAppBar(
        navigationIcon = {
            IconButton(onClick = onClickCloseSearch) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "delete")
            }
        },
        title = {
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
    )
    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(100)
        focusRequester.requestFocus()
    }
}
