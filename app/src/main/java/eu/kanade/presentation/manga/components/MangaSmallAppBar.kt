package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.R

@Composable
fun MangaSmallAppBar(
    modifier: Modifier = Modifier,
    title: String,
    titleAlphaProvider: () -> Float,
    backgroundAlphaProvider: () -> Float = titleAlphaProvider,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    onBackClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
) {
    val isActionMode = actionModeCounter > 0
    val backgroundAlpha = if (isActionMode) 1f else backgroundAlphaProvider()
    val backgroundColor by TopAppBarDefaults.centerAlignedTopAppBarColors().containerColor(1f)
    Column(
        modifier = modifier.drawBehind {
            drawRect(backgroundColor.copy(alpha = backgroundAlpha))
        },
    ) {
        SmallTopAppBar(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            title = {
                Text(
                    text = if (isActionMode) actionModeCounter.toString() else title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(titleAlphaProvider()),
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
                    if (onShareClicked != null) {
                        IconButton(onClick = onShareClicked) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.action_share),
                            )
                        }
                    }

                    if (onDownloadClicked != null) {
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
                                        onDownloadClicked(DownloadAction.NEXT_1_CHAPTER)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_5)) },
                                    onClick = {
                                        onDownloadClicked(DownloadAction.NEXT_5_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_10)) },
                                    onClick = {
                                        onDownloadClicked(DownloadAction.NEXT_10_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_custom)) },
                                    onClick = {
                                        onDownloadClicked(DownloadAction.CUSTOM)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_unread)) },
                                    onClick = {
                                        onDownloadClicked(DownloadAction.UNREAD_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.download_all)) },
                                    onClick = {
                                        onDownloadClicked(DownloadAction.ALL_CHAPTERS)
                                        onDismissRequest()
                                    },
                                )
                            }
                        }
                    }

                    if (onEditCategoryClicked != null && onMigrateClicked != null) {
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
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_edit_categories)) },
                                    onClick = {
                                        onEditCategoryClicked()
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_migrate)) },
                                    onClick = {
                                        onMigrateClicked()
                                        onDismissRequest()
                                    },
                                )
                            }
                        }
                    }
                }
            },
            // Background handled by parent
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        if (downloadedOnlyMode) {
            Text(
                text = stringResource(R.string.label_downloaded_only),
                modifier = Modifier
                    .background(color = MaterialTheme.colorScheme.tertiary)
                    .fillMaxWidth()
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.onTertiary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (incognitoMode) {
            Text(
                text = stringResource(R.string.pref_incognito_mode),
                modifier = Modifier
                    .background(color = MaterialTheme.colorScheme.primary)
                    .fillMaxWidth()
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
