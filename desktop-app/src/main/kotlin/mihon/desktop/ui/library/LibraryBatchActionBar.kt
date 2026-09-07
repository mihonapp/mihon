package mihon.desktop.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings

@Composable
fun LibraryBatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onChangeCategories: () -> Unit,
    onMarkRead: (Boolean) -> Unit,
    onDownloadChapters: (Int) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onExitSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var downloadMenuOpen by remember { mutableStateOf(false) }
    var confirmRemoveOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("library-batch-action-bar"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.libraryBatchSelected(selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("batch-selected-count"),
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (selectedCount < totalCount) {
                    TextButton(onClick = onSelectAll, modifier = Modifier.testTag("batch-select-all")) {
                        Text(strings.libraryBatchSelectAll)
                    }
                } else {
                    TextButton(onClick = onDeselectAll, modifier = Modifier.testTag("batch-deselect-all")) {
                        Text(strings.libraryBatchDeselectAll)
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Change Category
                FilledTonalButton(
                    onClick = onChangeCategories,
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("batch-change-category"),
                ) {
                    Text(strings.libraryBatchChangeCategory)
                }

                // Mark Read
                FilledTonalButton(
                    onClick = { onMarkRead(true) },
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("batch-mark-read"),
                ) {
                    Text(strings.libraryBatchMarkRead)
                }

                // Mark Unread
                FilledTonalButton(
                    onClick = { onMarkRead(false) },
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("batch-mark-unread"),
                ) {
                    Text(strings.libraryBatchMarkUnread)
                }

                // Download Dropdown
                Box {
                    FilledTonalButton(
                        onClick = { downloadMenuOpen = true },
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("batch-download-button"),
                    ) {
                        Text(strings.libraryBatchDownload)
                    }

                    DropdownMenu(
                        expanded = downloadMenuOpen,
                        onDismissRequest = { downloadMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.libraryBatchDownloadNext1) },
                            onClick = {
                                downloadMenuOpen = false
                                onDownloadChapters(1)
                            },
                            modifier = Modifier.testTag("batch-download-next1"),
                        )
                        DropdownMenuItem(
                            text = { Text(strings.libraryBatchDownloadNext5) },
                            onClick = {
                                downloadMenuOpen = false
                                onDownloadChapters(5)
                            },
                            modifier = Modifier.testTag("batch-download-next5"),
                        )
                        DropdownMenuItem(
                            text = { Text(strings.libraryBatchDownloadAllUnread) },
                            onClick = {
                                downloadMenuOpen = false
                                onDownloadChapters(-1)
                            },
                            modifier = Modifier.testTag("batch-download-all-unread"),
                        )
                    }
                }

                // Remove from library
                Button(
                    onClick = { confirmRemoveOpen = true },
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("batch-remove-button"),
                ) {
                    Text(strings.libraryBatchRemove)
                }

                // Done
                OutlinedButton(onClick = onExitSelection, modifier = Modifier.testTag("batch-exit-button")) {
                    Text(strings.libraryBatchDone)
                }
            }
        }
    }

    if (confirmRemoveOpen) {
        AlertDialog(
            onDismissRequest = { confirmRemoveOpen = false },
            title = { Text(strings.libraryBatchRemoveConfirmTitle) },
            text = { Text(strings.libraryBatchRemoveConfirmMessage(selectedCount)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveOpen = false
                        onRemoveFromLibrary()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("batch-remove-confirm"),
                ) {
                    Text(strings.libraryBatchRemove)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveOpen = false }) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }
}
