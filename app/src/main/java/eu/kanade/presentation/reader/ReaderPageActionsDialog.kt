package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import okio.Buffer
import tachiyomi.i18n.MR
import tachiyomi.core.common.util.system.ImageUtil
import eu.kanade.tachiyomi.source.model.Page
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    page: ReaderPage,
    hiddenUiState: ReaderViewModel.HiddenImageUiState,
    onSetAsCover: () -> Unit,
    onShare: (Boolean) -> Unit,
    onSave: () -> Unit,
    onToggleImageVisibility: (Boolean) -> Unit,
    onRemoveFromHidden: () -> Unit,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        BoxWithConstraints {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                PagePreview(
                    page = page,
                    maxPreviewHeight = (this@BoxWithConstraints.maxHeight * 0.35f).coerceAtMost(240.dp),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = if (
                            hiddenUiState.isInHiddenList &&
                                hiddenUiState.isHidden
                        ) {
                            stringResource(MR.strings.hidden_images_action_show)
                        } else {
                            stringResource(MR.strings.hidden_images_hide_image)
                        },
                        icon = Icons.Outlined.VisibilityOff,
                        onClick = {
                            onToggleImageVisibility(
                                hiddenUiState.isInHiddenList &&
                                    hiddenUiState.isHidden,
                            )
                        },
                    )
                    if (hiddenUiState.isInHiddenList) {
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            title = stringResource(MR.strings.hidden_images_remove_from_hidden),
                            icon = Icons.Outlined.Delete,
                            onClick = onRemoveFromHidden,
                        )
                    }
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(MR.strings.set_as_cover),
                        icon = Icons.Outlined.Photo,
                        onClick = { showSetCoverDialog = true },
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(MR.strings.action_copy_to_clipboard),
                        icon = Icons.Outlined.ContentCopy,
                        onClick = {
                            onShare(true)
                            onDismissRequest()
                        },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(MR.strings.action_share),
                        icon = Icons.Outlined.Share,
                        onClick = {
                            onShare(false)
                            onDismissRequest()
                        },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(MR.strings.action_save),
                        icon = Icons.Outlined.Save,
                        onClick = {
                            onSave()
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }

    if (showSetCoverDialog) {
        SetCoverDialog(
            onConfirm = {
                onSetAsCover()
                showSetCoverDialog = false
            },
            onDismiss = { showSetCoverDialog = false },
        )
    }
}

@Composable
private fun PagePreview(page: ReaderPage, maxPreviewHeight: Dp) {
    val previewState by produceState(initialValue = PreviewState(source = null, isAnimated = false), key1 = page) {
        page.statusFlow.collect { pageState ->
            if (pageState != Page.State.Ready) {
                value = PreviewState(source = null, isAnimated = false)
                return@collect
            }

            value = withContext(Dispatchers.IO) {
                runCatching {
                    val streamProvider = page.stream ?: return@runCatching PreviewState(source = null, isAnimated = false)
                    val source = streamProvider().use { Buffer().readFrom(it) }
                    val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                    PreviewState(source = source, isAnimated = isAnimated)
                }.getOrDefault(PreviewState(source = null, isAnimated = false))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.padding.small))
            .padding(horizontal = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = maxPreviewHeight.coerceAtLeast(120.dp))
                .padding(12.dp),
        ) {
            val source = previewState.source
            if (source != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AndroidView(
                        factory = { context ->
                            ReaderPageImageView(context).apply {
                                tag = null
                            }
                        },
                        update = { view ->
                            if (view.tag !== source) {
                                view.setImage(
                                    source,
                                    previewState.isAnimated,
                                    ReaderPageImageView.Config(zoomDuration = 0),
                                )
                                view.tag = source
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    text = stringResource(MR.strings.loading),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private data class PreviewState(
    val source: okio.BufferedSource?,
    val isAnimated: Boolean,
)

@Composable
private fun SetCoverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(stringResource(MR.strings.confirm_set_image_as_cover))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}
