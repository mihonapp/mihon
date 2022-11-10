package eu.kanade.presentation.manga.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.updatePadding
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.util.clickableNoIndication
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView

@Composable
fun MangaCoverDialog(
    coverDataProvider: () -> Manga,
    isCustomCover: Boolean,
    snackbarHostState: SnackbarHostState,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: ((EditCoverAction) -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false, // Doesn't work https://issuetracker.google.com/issues/246909281
        ),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .navigationBarsPadding(),
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.action_share),
                        )
                    }
                    IconButton(onClick = onSaveClick) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = stringResource(R.string.action_save),
                        )
                    }
                    if (onEditClick != null) {
                        Box {
                            var expanded by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    if (isCustomCover) {
                                        expanded = true
                                    } else {
                                        onEditClick(EditCoverAction.EDIT)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.action_edit_cover),
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                offset = DpOffset(8.dp, 0.dp),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_edit)) },
                                    onClick = {
                                        onEditClick(EditCoverAction.EDIT)
                                        expanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_delete)) },
                                    onClick = {
                                        onEditClick(EditCoverAction.DELETE)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
        ) { contentPadding ->
            val statusBarPaddingPx = WindowInsets.systemBars.getTop(LocalDensity.current)
            val bottomPaddingPx = with(LocalDensity.current) { contentPadding.calculateBottomPadding().roundToPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
                    .clickableNoIndication(onClick = onDismissRequest),
            ) {
                AndroidView(
                    factory = {
                        ReaderPageImageView(it).apply {
                            onViewClicked = onDismissRequest
                            clipToPadding = false
                            clipChildren = false
                        }
                    },
                    update = { view ->
                        val request = ImageRequest.Builder(view.context)
                            .data(coverDataProvider())
                            .size(Size.ORIGINAL)
                            .target { drawable ->
                                // Copy bitmap in case it came from memory cache
                                // Because SSIV needs to thoroughly read the image
                                val copy = (drawable as? BitmapDrawable)?.let {
                                    val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        Bitmap.Config.HARDWARE
                                    } else {
                                        Bitmap.Config.ARGB_8888
                                    }
                                    BitmapDrawable(
                                        view.context.resources,
                                        it.bitmap.copy(config, false),
                                    )
                                } ?: drawable
                                view.setImage(copy, ReaderPageImageView.Config(zoomDuration = 500))
                            }
                            .build()
                        view.context.imageLoader.enqueue(request)

                        view.updatePadding(top = statusBarPaddingPx, bottom = bottomPaddingPx)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
