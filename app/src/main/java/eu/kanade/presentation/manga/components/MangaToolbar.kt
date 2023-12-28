package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.manga.DownloadAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun MangaToolbar(
    title: String,
    titleAlphaProvider: () -> Float,
    hasFilters: Boolean,
    onBackClicked: () -> Unit,
    onClickFilter: () -> Unit,
    onClickShare: (() -> Unit)?,
    onClickDownload: ((DownloadAction) -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickRefresh: () -> Unit,
    onClickMigrate: (() -> Unit)?,

    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,

    modifier: Modifier = Modifier,
    backgroundAlphaProvider: () -> Float = titleAlphaProvider,
) {
    Column(
        modifier = modifier,
    ) {
        val isActionMode = actionModeCounter > 0
        TopAppBar(
            title = {
                Text(
                    text = if (isActionMode) actionModeCounter.toString() else title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = if (isActionMode) 1f else titleAlphaProvider()),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClicked) {
                    UpIcon(navigationIcon = Icons.Outlined.Close.takeIf { isActionMode })
                }
            },
            actions = {
                if (isActionMode) {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = Icons.Outlined.SelectAll,
                                onClick = onSelectAll,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_inverse),
                                icon = Icons.Outlined.FlipToBack,
                                onClick = onInvertSelection,
                            ),
                        ),
                    )
                } else {
                    var downloadExpanded by remember { mutableStateOf(false) }
                    if (onClickDownload != null) {
                        val onDismissRequest = { downloadExpanded = false }
                        DownloadDropdownMenu(
                            expanded = downloadExpanded,
                            onDismissRequest = onDismissRequest,
                            onDownloadClicked = onClickDownload,
                        )
                    }

                    val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
                    AppBarActions(
                        actions = persistentListOf<AppBar.AppBarAction>().builder()
                            .apply {
                                if (onClickDownload != null) {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.manga_download),
                                            icon = Icons.Outlined.Download,
                                            onClick = { downloadExpanded = !downloadExpanded },
                                        ),
                                    )
                                }
                                add(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_filter),
                                        icon = Icons.Outlined.FilterList,
                                        iconTint = filterTint,
                                        onClick = onClickFilter,
                                    ),
                                )
                                add(
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_webview_refresh),
                                        onClick = onClickRefresh,
                                    ),
                                )
                                if (onClickEditCategory != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_edit_categories),
                                            onClick = onClickEditCategory,
                                        ),
                                    )
                                }
                                if (onClickMigrate != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_migrate),
                                            onClick = onClickMigrate,
                                        ),
                                    )
                                }
                                if (onClickShare != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_share),
                                            onClick = onClickShare,
                                        ),
                                    )
                                }
                            }
                            .build(),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme
                    .surfaceColorAtElevation(3.dp)
                    .copy(alpha = if (isActionMode) 1f else backgroundAlphaProvider()),
            ),
        )
    }
}
