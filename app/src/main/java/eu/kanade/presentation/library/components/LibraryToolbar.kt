package eu.kanade.presentation.library.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.Pill
import eu.kanade.presentation.library.LibraryState
import eu.kanade.presentation.theme.active
import kotlinx.coroutines.delay

@Composable
fun LibraryToolbar(
    state: LibraryState,
    title: LibraryToolbarTitle,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
) = when {
    state.selectionMode -> LibrarySelectionToolbar(
        state = state,
        onClickUnselectAll = onClickUnselectAll,
        onClickSelectAll = onClickSelectAll,
        onClickInvertSelection = onClickInvertSelection,
    )
    state.searchQuery != null -> LibrarySearchToolbar(
        searchQuery = state.searchQuery!!,
        onChangeSearchQuery = { state.searchQuery = it },
        onClickCloseSearch = { state.searchQuery = null },
    )
    else -> LibraryRegularToolbar(
        title = title,
        hasFilters = state.hasActiveFilters,
        onClickSearch = { state.searchQuery = "" },
        onClickFilter = onClickFilter,
        onClickRefresh = onClickRefresh,
    )
}

@Composable
fun LibraryRegularToolbar(
    title: LibraryToolbarTitle,
    hasFilters: Boolean,
    onClickSearch: () -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
    val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
    SmallTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, false),
                    overflow = TextOverflow.Ellipsis,
                )
                if (title.numberOfManga != null) {
                    Pill(
                        text = "${title.numberOfManga}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                        fontSize = 14.sp,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onClickSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "search")
            }
            IconButton(onClick = onClickFilter) {
                Icon(Icons.Outlined.FilterList, contentDescription = "search", tint = filterTint)
            }
            IconButton(onClick = onClickRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "search")
            }
        },
    )
}

@Composable
fun LibrarySelectionToolbar(
    state: LibraryState,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
) {
    val backgroundColor by TopAppBarDefaults.smallTopAppBarColors().containerColor(1f)
    SmallTopAppBar(
        modifier = Modifier
            .drawBehind {
                drawRect(backgroundColor.copy(alpha = 1f))
            },
        navigationIcon = {
            IconButton(onClick = onClickUnselectAll) {
                Icon(Icons.Outlined.Close, contentDescription = "close")
            }
        },
        title = {
            Text(text = "${state.selection.size}")
        },
        actions = {
            IconButton(onClick = onClickSelectAll) {
                Icon(Icons.Outlined.SelectAll, contentDescription = "search")
            }
            IconButton(onClick = onClickInvertSelection) {
                Icon(Icons.Outlined.FlipToBack, contentDescription = "invert")
            }
        },
        colors = TopAppBarDefaults.smallTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

@Composable
fun LibrarySearchToolbar(
    searchQuery: String,
    onChangeSearchQuery: (String) -> Unit,
    onClickCloseSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester.Default }
    SmallTopAppBar(
        navigationIcon = {
            IconButton(onClick = onClickCloseSearch) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "back")
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
            LaunchedEffect(focusRequester) {
                // TODO: https://issuetracker.google.com/issues/204502668
                delay(100)
                focusRequester.requestFocus()
            }
        },
    )
}

data class LibraryToolbarTitle(
    val text: String,
    val numberOfManga: Int? = null,
)
