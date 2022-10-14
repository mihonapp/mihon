package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.theme.active
import eu.kanade.tachiyomi.R

@Composable
fun MangaToolbar(
    modifier: Modifier = Modifier,
    title: String,
    titleAlphaProvider: () -> Float,
    backgroundAlphaProvider: () -> Float = titleAlphaProvider,
    hasFilters: Boolean,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    onBackClicked: () -> Unit,
    onClickFilter: () -> Unit,
    onClickShare: (() -> Unit)?,
    onClickDownload: ((DownloadAction) -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickMigrate: (() -> Unit)?,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
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
                    modifier = Modifier.alpha(if (isActionMode) 1f else titleAlphaProvider()),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = if (isActionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.abc_action_bar_up_description),
                    )
                }
            },
            actions = {
                if (isActionMode) {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = stringResource(R.string.action_select_all),
                        )
                    }
                    IconButton(onClick = onInvertSelection) {
                        Icon(
                            imageVector = Icons.Default.FlipToBack,
                            contentDescription = stringResource(R.string.action_select_inverse),
                        )
                    }
                } else {
                    if (onClickDownload != null) {
                        val (downloadExpanded, onDownloadExpanded) = remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { onDownloadExpanded(!downloadExpanded) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = stringResource(R.string.manga_download),
                                )
                            }
                            val onDismissRequest = { onDownloadExpanded(false) }
                            DropdownMenu(
                                expanded = downloadExpanded,
                                onDismissRequest = onDismissRequest,
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_1)) },
                                    onClick = {
                                        onClickDownload(DownloadAction.NEXT_1_CHAPTER)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_5)) },
                                    onClick = {
                                        onClickDownload(DownloadAction.NEXT_5_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_10)) },
                                    onClick = {
                                        onClickDownload(DownloadAction.NEXT_10_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_custom)) },
                                    onClick = {
                                        onClickDownload(DownloadAction.CUSTOM)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_unread)) },
                                    onClick = {
                                        onClickDownload(DownloadAction.UNREAD_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_all)) },
                                    onClick = {
                                        onClickDownload(DownloadAction.ALL_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                            }
                        }
                    }

                    val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
                    IconButton(onClick = onClickFilter) {
                        Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.action_filter), tint = filterTint)
                    }

                    if (onClickEditCategory != null && onClickMigrate != null) {
                        val (moreExpanded, onMoreExpanded) = remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { onMoreExpanded(!moreExpanded) }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.abc_action_menu_overflow_description),
                                )
                            }
                            val onDismissRequest = { onMoreExpanded(false) }
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = onDismissRequest,
                            ) {
                                if (onClickShare != null) {
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.action_share)) },
                                        onClick = {
                                            onClickShare()
                                            onDismissRequest()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_edit_categories)) },
                                    onClick = {
                                        onClickEditCategory()
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_migrate)) },
                                    onClick = {
                                        onClickMigrate()
                                        onDismissRequest()
                                    },
                                )
                            }
                        }
                    }
                }
            },
            windowInsets = WindowInsets.statusBars,
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme
                    .surfaceColorAtElevation(3.dp)
                    .copy(alpha = if (isActionMode) 1f else backgroundAlphaProvider()),
            ),
        )

        AppStateBanners(downloadedOnlyMode, incognitoMode)
    }
}
