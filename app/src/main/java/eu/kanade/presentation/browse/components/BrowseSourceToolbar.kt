package eu.kanade.presentation.browse.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.presentation.browse.BrowseSourceState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource

@Composable
fun BrowseSourceToolbar(
    state: BrowseSourceState,
    source: CatalogueSource,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSearch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    if (state.searchQuery == null) {
        BrowseSourceRegularToolbar(
            title = if (state.isUserQuery) state.currentFilter.query else source.name,
            isLocalSource = source is LocalSource,
            displayMode = displayMode,
            onDisplayModeChange = onDisplayModeChange,
            navigateUp = navigateUp,
            onSearchClick = { state.searchQuery = if (state.isUserQuery) state.currentFilter.query else "" },
            onWebViewClick = onWebViewClick,
            onHelpClick = onHelpClick,
            scrollBehavior = scrollBehavior,
        )
    } else {
        val cancelSearch = { state.searchQuery = null }
        BrowseSourceSearchToolbar(
            searchQuery = state.searchQuery!!,
            onSearchQueryChanged = { state.searchQuery = it },
            placeholderText = stringResource(R.string.action_search_hint),
            navigateUp = cancelSearch,
            onResetClick = { state.searchQuery = "" },
            onSearchClick = {
                onSearch(it)
                cancelSearch()
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
fun BrowseSourceRegularToolbar(
    title: String,
    isLocalSource: Boolean,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onSearchClick: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    AppBar(
        navigateUp = navigateUp,
        title = title,
        actions = {
            var selectingDisplayMode by remember { mutableStateOf(false) }
            AppBarActions(
                actions = listOf(
                    AppBar.Action(
                        title = stringResource(R.string.action_search),
                        icon = Icons.Outlined.Search,
                        onClick = onSearchClick,
                    ),
                    AppBar.Action(
                        title = stringResource(R.string.action_display_mode),
                        icon = if (displayMode == LibraryDisplayMode.List) Icons.Filled.ViewList else Icons.Filled.ViewModule,
                        onClick = { selectingDisplayMode = true },
                    ),
                    if (isLocalSource) {
                        AppBar.Action(
                            title = stringResource(R.string.label_help),
                            icon = Icons.Outlined.Help,
                            onClick = onHelpClick,
                        )
                    } else {
                        AppBar.Action(
                            title = stringResource(R.string.action_web_view),
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
                RadioMenuItem(
                    text = { Text(text = stringResource(R.string.action_display_comfortable_grid)) },
                    isChecked = displayMode == LibraryDisplayMode.ComfortableGrid,
                ) {
                    onDisplayModeChange(LibraryDisplayMode.ComfortableGrid)
                }
                RadioMenuItem(
                    text = { Text(text = stringResource(R.string.action_display_grid)) },
                    isChecked = displayMode == LibraryDisplayMode.CompactGrid,
                ) {
                    onDisplayModeChange(LibraryDisplayMode.CompactGrid)
                }
                RadioMenuItem(
                    text = { Text(text = stringResource(R.string.action_display_list)) },
                    isChecked = displayMode == LibraryDisplayMode.List,
                ) {
                    onDisplayModeChange(LibraryDisplayMode.List)
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun BrowseSourceSearchToolbar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    placeholderText: String?,
    navigateUp: () -> Unit,
    onResetClick: () -> Unit,
    onSearchClick: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    SearchToolbar(
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChanged,
        placeholderText = placeholderText,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearchClick(searchQuery)
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        ),
        onClickCloseSearch = navigateUp,
        onClickResetSearch = onResetClick,
        scrollBehavior = scrollBehavior,
    )
}
