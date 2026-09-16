package mihon.desktop.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

@Composable
fun MangaBottomActionMenu(
    visible: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    onMarkAsReadClicked: (() -> Unit)? = null,
    onMarkAsUnreadClicked: (() -> Unit)? = null,
    onMarkPreviousAsReadClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
    onCloseClicked: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val strings = LocalStrings.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("manga-bottom-action-menu"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left side: close button, selection count & selection toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onCloseClicked,
                    modifier = Modifier.testTag("batch-chapter-close"),
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = strings.text(UiText.CloseSelection))
                }
                Text(
                    text = strings.chapterBatchSelected(selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("batch-chapter-selected-count"),
                )
                TextButton(
                    onClick = onSelectAll,
                    modifier = Modifier.testTag("batch-chapter-select-all"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(strings.chapterBatchSelectAll)
                }
                TextButton(
                    onClick = onInvertSelection,
                    modifier = Modifier.testTag("batch-chapter-invert"),
                ) {
                    Text(strings.chapterBatchInvert)
                }
            }

            // Right side: batch action buttons
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBookmarkClicked != null) {
                    FilledTonalButton(
                        onClick = onBookmarkClicked,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("batch-chapter-bookmark"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.chapterBatchBookmark)
                    }
                }

                if (onRemoveBookmarkClicked != null) {
                    FilledTonalButton(
                        onClick = onRemoveBookmarkClicked,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("batch-chapter-remove-bookmark"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.chapterBatchRemoveBookmark)
                    }
                }

                if (onMarkAsReadClicked != null) {
                    FilledTonalButton(
                        onClick = onMarkAsReadClicked,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("batch-chapter-mark-read"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.chapterBatchMarkAsRead)
                    }
                }

                if (onMarkAsUnreadClicked != null) {
                    FilledTonalButton(
                        onClick = onMarkAsUnreadClicked,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("batch-chapter-mark-unread"),
                    ) {
                        Text(strings.chapterBatchMarkAsUnread)
                    }
                }

                if (onMarkPreviousAsReadClicked != null && selectedCount == 1) {
                    FilledTonalButton(
                        onClick = onMarkPreviousAsReadClicked,
                        modifier = Modifier.testTag("batch-chapter-mark-previous-read"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.markPreviousAsRead)
                    }
                }

                if (onDownloadClicked != null) {
                    FilledTonalButton(
                        onClick = onDownloadClicked,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("batch-chapter-download"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.chapterBatchDownload)
                    }
                }

                if (onDeleteClicked != null) {
                    OutlinedButton(
                        onClick = onDeleteClicked,
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.testTag("batch-chapter-delete-download"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.chapterBatchDeleteDownload)
                    }
                }
            }
        }
    }
}
