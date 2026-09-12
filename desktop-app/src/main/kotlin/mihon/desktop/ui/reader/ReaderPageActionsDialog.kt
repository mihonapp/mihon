package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Mihon-style page actions. Buttons only emit callbacks so the surface is trivially testable and
 * the reader screen owns the asynchronous/platform work.
 */
@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSetAsCover: () -> Unit,
    onOpenInBrowser: () -> Unit,
    canSetAsCover: Boolean = true,
    canOpenInBrowser: Boolean = false,
    busy: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            }
            .testTag("reader-page-actions-dialog"),
        title = {
            Text(
                text = "Page actions",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PageActionButton(
                    tag = "reader-page-action-save",
                    label = "Save page image",
                    icon = Icons.Rounded.Save,
                    enabled = !busy,
                    onClick = onSave,
                )
                PageActionButton(
                    tag = "reader-page-action-copy",
                    label = "Copy image",
                    icon = Icons.Rounded.ContentCopy,
                    enabled = !busy,
                    onClick = onCopy,
                )
                PageActionButton(
                    tag = "reader-page-action-share",
                    label = "Share image",
                    icon = Icons.Rounded.Share,
                    enabled = !busy,
                    onClick = onShare,
                )
                PageActionButton(
                    tag = "reader-page-action-cover",
                    label = "Set as manga cover",
                    icon = Icons.Rounded.Photo,
                    enabled = !busy && canSetAsCover,
                    onClick = onSetAsCover,
                )
                if (canOpenInBrowser) {
                    PageActionButton(
                        tag = "reader-page-action-browser",
                        label = "Open page in browser",
                        icon = Icons.Rounded.OpenInBrowser,
                        enabled = !busy,
                        onClick = onOpenInBrowser,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onDismissRequest()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("reader-page-actions-cancel"),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PageActionButton(
    tag: String,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.width(12.dp))
            Text(text = label, modifier = Modifier.weight(1f))
        }
    }
}
