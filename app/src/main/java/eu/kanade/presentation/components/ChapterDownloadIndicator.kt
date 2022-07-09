package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.ChapterDownloadAction
import eu.kanade.presentation.util.secondaryItemAlpha
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download

@Composable
fun ChapterDownloadIndicator(
    modifier: Modifier = Modifier,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    val downloadState = downloadStateProvider()
    val isDownloaded = downloadState == Download.State.DOWNLOADED
    val isDownloading = downloadState != Download.State.NOT_DOWNLOADED
    var isMenuExpanded by remember(downloadState) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .combinedClickable(
                onLongClick = {
                    val chapterDownloadAction = when {
                        isDownloaded -> ChapterDownloadAction.DELETE
                        isDownloading -> ChapterDownloadAction.CANCEL
                        else -> ChapterDownloadAction.START_NOW
                    }
                    onClick(chapterDownloadAction)
                },
                onClick = {
                    if (isDownloaded || isDownloading) {
                        isMenuExpanded = true
                    } else {
                        onClick(ChapterDownloadAction.START)
                    }
                },
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(
                    bounded = false,
                    radius = IconButtonTokens.StateLayerSize / 2,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isDownloaded) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(IndicatorSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.action_delete)) },
                    onClick = {
                        onClick(ChapterDownloadAction.DELETE)
                        isMenuExpanded = false
                    },
                )
            }
        } else {
            val inactiveAlphaModifier = if (!isDownloading) Modifier.secondaryItemAlpha() else Modifier
            val arrowColor: Color
            val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
            if (isDownloading) {
                val downloadProgress = downloadProgressProvider()
                val indeterminate = downloadState == Download.State.QUEUE ||
                    (downloadState == Download.State.DOWNLOADING && downloadProgress == 0)
                if (indeterminate) {
                    arrowColor = strokeColor
                    CircularProgressIndicator(
                        modifier = IndicatorModifier,
                        color = strokeColor,
                        strokeWidth = IndicatorStrokeWidth,
                    )
                } else {
                    val animatedProgress by animateFloatAsState(
                        targetValue = downloadProgress / 100f,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    )
                    arrowColor = if (animatedProgress < 0.5f) {
                        strokeColor
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                    CircularProgressIndicator(
                        progress = animatedProgress,
                        modifier = IndicatorModifier,
                        color = strokeColor,
                        strokeWidth = IndicatorSize / 2,
                    )
                }
                DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_start_downloading_now)) },
                        onClick = {
                            onClick(ChapterDownloadAction.START_NOW)
                            isMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_cancel)) },
                        onClick = {
                            onClick(ChapterDownloadAction.CANCEL)
                            isMenuExpanded = false
                        },
                    )
                }
            } else {
                arrowColor = strokeColor
                CircularProgressIndicator(
                    progress = 1f,
                    modifier = IndicatorModifier.then(inactiveAlphaModifier),
                    color = strokeColor,
                    strokeWidth = IndicatorStrokeWidth,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = ArrowModifier.then(inactiveAlphaModifier),
                tint = arrowColor,
            )
        }
    }
}

private val IndicatorSize = 26.dp
private val IndicatorPadding = 2.dp

// To match composable parameter name when used later
private val IndicatorStrokeWidth = IndicatorPadding

private val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
private val ArrowModifier = Modifier
    .size(IndicatorSize - 7.dp)
