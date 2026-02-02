package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun BrowseSourceToolbar(
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    source: Source?,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    showPageNumber: Boolean = false,
    currentPage: Int = 1,
    onPageJump: ((Int) -> Unit)? = null,
    onPageRangeLoad: ((startPage: Int, endPage: Int) -> Unit)? = null,
) {
    // Avoid capturing unstable source in actions lambda
    val title = source?.name
    val isLocalSource = source is LocalSource
    val isConfigurableSource = source is ConfigurableSource

    var selectingDisplayMode by remember { mutableStateOf(false) }
    var showPageJumpDialog by remember { mutableStateOf(false) }

    SearchToolbar(
        navigateUp = navigateUp,
        titleContent = { AppBarTitle(title) },
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        onSearch = onSearch,
        onClickCloseSearch = navigateUp,
        actions = {
            // Page number indicator (clickable to jump to page)
            if (showPageNumber && onPageJump != null) {
                TextButton(
                    onClick = { showPageJumpDialog = true },
                ) {
                    Text(
                        text = "P.$currentPage",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }
            
            AppBarActions(
                actions = persistentListOf<AppBar.AppBarAction>().builder()
                    .apply {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_display_mode),
                                icon = if (displayMode == LibraryDisplayMode.List) {
                                    Icons.AutoMirrored.Filled.ViewList
                                } else {
                                    Icons.Filled.ViewModule
                                },
                                onClick = { selectingDisplayMode = true },
                            ),
                        )
                        if (isLocalSource) {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.label_help),
                                    onClick = onHelpClick,
                                ),
                            )
                        } else {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_open_in_web_view),
                                    onClick = onWebViewClick,
                                ),
                            )
                        }
                        if (isConfigurableSource) {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_settings),
                                    onClick = onSettingsClick,
                                ),
                            )
                        }
                    }
                    .build(),
            )

            DropdownMenu(
                expanded = selectingDisplayMode,
                onDismissRequest = { selectingDisplayMode = false },
            ) {
                RadioMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_comfortable_grid)) },
                    isChecked = displayMode == LibraryDisplayMode.ComfortableGrid,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.ComfortableGrid)
                }
                RadioMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_grid)) },
                    isChecked = displayMode == LibraryDisplayMode.CompactGrid,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.CompactGrid)
                }
                RadioMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_list)) },
                    isChecked = displayMode == LibraryDisplayMode.List,
                ) {
                    selectingDisplayMode = false
                    onDisplayModeChange(LibraryDisplayMode.List)
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )

    if (showPageJumpDialog && onPageJump != null) {
        var pageInput by remember { mutableStateOf(currentPage.toString()) }
        var endPageInput by remember { mutableStateOf("") }
        var showRangeMode by remember { mutableStateOf(false) }
        
        // Get current delay from preferences
        val sourcePreferences = remember { Injekt.get<eu.kanade.domain.source.service.SourcePreferences>() }
        var currentDelay by remember { mutableIntStateOf(sourcePreferences.pageLoadDelay().get()) }
        
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false },
            title = { Text(if (showRangeMode) "Load Page Range" else "Jump to Page") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { char -> char.isDigit() } },
                        label = { Text(if (showRangeMode) "Start page" else "Page number") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (showRangeMode) ImeAction.Next else ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (!showRangeMode) {
                                    pageInput.toIntOrNull()?.let { page ->
                                        if (page > 0) {
                                            onPageJump(page)
                                            showPageJumpDialog = false
                                        }
                                    }
                                }
                            },
                        ),
                        singleLine = true,
                    )
                    
                    if (showRangeMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = endPageInput,
                            onValueChange = { endPageInput = it.filter { char -> char.isDigit() } },
                            label = { Text("End page") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val startPage = pageInput.toIntOrNull()
                                    val endPage = endPageInput.toIntOrNull()
                                    if (startPage != null && endPage != null && startPage > 0 && endPage >= startPage) {
                                        sourcePreferences.pageLoadDelay().set(currentDelay)
                                        if (onPageRangeLoad != null) {
                                            onPageRangeLoad(startPage, endPage)
                                        } else {
                                            onPageJump(startPage)
                                        }
                                        showPageJumpDialog = false
                                    }
                                },
                            ),
                            singleLine = true,
                        )
                        
                        // Pagination delay slider
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Pagination delay: ${currentDelay}s",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Slider(
                            value = currentDelay.toFloat(),
                            onValueChange = { currentDelay = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        
                        Text(
                            text = "Delay between loading each page in the range",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    
                    TextButton(
                        onClick = { showRangeMode = !showRangeMode },
                    ) {
                        Text(if (showRangeMode) "Single page mode" else "Load page range")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (showRangeMode) {
                            val startPage = pageInput.toIntOrNull()
                            val endPage = endPageInput.toIntOrNull()
                            if (startPage != null && endPage != null && startPage > 0 && endPage >= startPage) {
                                // Save delay preference when loading range
                                sourcePreferences.pageLoadDelay().set(currentDelay)
                                if (onPageRangeLoad != null) {
                                    onPageRangeLoad(startPage, endPage)
                                } else {
                                    onPageJump(startPage)
                                }
                                showPageJumpDialog = false
                            }
                        } else {
                            pageInput.toIntOrNull()?.let { page ->
                                if (page > 0) {
                                    onPageJump(page)
                                    showPageJumpDialog = false
                                }
                            }
                        }
                    },
                ) {
                    Text(if (showRangeMode) "Load Range" else "Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJumpDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
