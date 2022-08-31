package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import eu.kanade.presentation.browse.BrowseSourceState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode
import kotlinx.coroutines.delay

@Composable
fun BrowseSourceToolbar(
    state: BrowseSourceState,
    source: CatalogueSource,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSearch: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    if (state.searchQuery == null) {
        BrowseSourceRegularToolbar(
            source = source,
            displayMode = displayMode,
            onDisplayModeChange = onDisplayModeChange,
            navigateUp = navigateUp,
            onSearchClick = { state.searchQuery = "" },
            onWebViewClick = onWebViewClick,
            onHelpClick = onHelpClick,
            scrollBehavior = scrollBehavior,
        )
    } else {
        BrowseSourceSearchToolbar(
            searchQuery = state.searchQuery!!,
            onSearchQueryChanged = { state.searchQuery = it },
            navigateUp = {
                state.searchQuery = null
                onSearch()
            },
            onResetClick = { state.searchQuery = "" },
            onSearchClick = onSearch,
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
fun BrowseSourceRegularToolbar(
    source: CatalogueSource,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onSearchClick: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        navigateUp = navigateUp,
        title = source.name,
        actions = {
            var selectingDisplayMode by remember { mutableStateOf(false) }
            AppBarActions(
                actions = listOf(
                    AppBar.Action(
                        title = "search",
                        icon = Icons.Outlined.Search,
                        onClick = onSearchClick,
                    ),
                    AppBar.Action(
                        title = "display_mode",
                        icon = Icons.Filled.ViewModule,
                        onClick = { selectingDisplayMode = true },
                    ),
                    if (source is LocalSource) {
                        AppBar.Action(
                            title = "help",
                            icon = Icons.Outlined.Help,
                            onClick = onHelpClick,
                        )
                    } else {
                        AppBar.Action(
                            title = "webview",
                            icon = Icons.Outlined.Public,
                            onClick = onWebViewClick,
                        )
                    },
                ),
            )
            DropdownMenu(
                expanded = selectingDisplayMode,
                onDismissRequest = { selectingDisplayMode = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_display_comfortable_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.ComfortableGrid) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.ComfortableGrid) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_display_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.CompactGrid) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.CompactGrid) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_display_list)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.List) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.List) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun BrowseSourceSearchToolbar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    navigateUp: () -> Unit,
    onResetClick: () -> Unit,
    onSearchClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val focusRequester = remember { FocusRequester() }
    AppBar(
        navigateUp = navigateUp,
        titleContent = {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearchClick()
                    },
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            )
        },
        actions = {
            AppBarActions(
                actions = listOf(
                    AppBar.Action(
                        title = "clear",
                        icon = Icons.Outlined.Clear,
                        onClick = onResetClick,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
    LaunchedEffect(Unit) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(100)
        focusRequester.requestFocus()
    }
}
