package eu.kanade.presentation.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.i18n.MR
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
                                hiddenUiState.renderState != ReaderViewModel.HiddenImageRenderState.VISIBLE
                        ) {
                            stringResource(MR.strings.hidden_images_action_show)
                        } else {
                            stringResource(MR.strings.hidden_images_hide_image)
                        },
                        icon = Icons.Outlined.VisibilityOff,
                        onClick = {
                            onToggleImageVisibility(
                                hiddenUiState.isInHiddenList &&
                                    hiddenUiState.renderState != ReaderViewModel.HiddenImageRenderState.VISIBLE,
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
    val previewState by produceState(initialValue = PreviewState(bitmap = null, aspectRatio = 1f), key1 = page) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val streamBytes = page.stream?.invoke()?.use { it.readBytes() }
                val bitmap = streamBytes?.let(::decodeSampledBitmap)
                    ?: decodeBitmapFromLocalPath(page.imageUrl ?: page.url)

                PreviewState(
                    bitmap = bitmap,
                    aspectRatio = if (bitmap != null && bitmap.height != 0) {
                        (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.3f, 2.2f)
                    } else {
                        1f
                    },
                )
            }.getOrDefault(PreviewState(bitmap = null, aspectRatio = 1f))
        }
    }

    Surface(
        shape = RoundedCornerShape(MaterialTheme.padding.small),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = maxPreviewHeight.coerceAtLeast(120.dp))
                .padding(12.dp),
        ) {
            val bitmap = previewState.bitmap
            if (bitmap != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .aspectRatio(previewState.aspectRatio)
                            .widthIn(max = this@BoxWithConstraints.maxWidth),
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
    val bitmap: android.graphics.Bitmap?,
    val aspectRatio: Float,
)

private fun decodeSampledBitmap(bytes: ByteArray): android.graphics.Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

    val maxDimension = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
    val targetMaxDimension = 1400
    var inSampleSize = 1
    while (maxDimension / inSampleSize > targetMaxDimension) {
        inSampleSize *= 2
    }

    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        },
    )
}

private fun decodeBitmapFromLocalPath(path: String?): android.graphics.Bitmap? {
    val rawPath = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val filePath = rawPath.removePrefix("file://")
    return BitmapFactory.decodeFile(filePath)
}

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
