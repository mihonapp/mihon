package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode

@Composable
fun BrowseLatestToolbar(
    navigateUp: () -> Unit,
    source: CatalogueSource,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onHelpClick: () -> Unit,
    onWebViewClick: () -> Unit,
) {
    AppBar(
        navigateUp = navigateUp,
        title = source.name,
        actions = {
            var selectingDisplayMode by remember { mutableStateOf(false) }
            AppBarActions(
                actions = listOf(
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
    )
}
