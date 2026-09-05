package mihon.desktop.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.history.DesktopHistoryGroup
import mihon.desktop.library.model.HistoryWithDetails
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    groups: List<DesktopHistoryGroup>,
    query: String,
    onQueryChange: (String) -> Unit,
    onReadChapter: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().testTag("history-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Resume recently read chapters and track reading activity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (groups.isNotEmpty() || query.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showClearConfirmation = true },
                    modifier = Modifier.testTag("history-clear-all-button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear history")
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("history-search"),
            label = { Text("Search history by manga or chapter") },
            singleLine = true,
        )

        // Content
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("history-empty-state"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (query.isBlank()) "No reading history recorded yet" else "No matching history found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("history-list"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                groups.forEach { group ->
                    item(key = "header-${group.title}") {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp).testTag("history-group-${group.title}"),
                        )
                    }

                    items(group.items, key = { it.chapterId }) { item ->
                        HistoryItemRow(
                            item = item,
                            onRead = { onReadChapter(item.chapterId) },
                            onDelete = { onDeleteItem(item.chapterId) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear reading history?") },
            text = {
                Text(
                    "This will permanently remove all reading history entries. Read chapters in your library will remain marked as read.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearAll()
                    },
                    modifier = Modifier.testTag("history-confirm-clear-button"),
                ) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun HistoryItemRow(
    item: HistoryWithDetails,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeStr = remember(item.lastRead) {
        Instant.ofEpochMilli(item.lastRead).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history-item-${item.chapterId}")
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail placeholder or art
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.mangaTitle.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.mangaTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.chapterName} • Read at $timeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.readDuration > 0) {
                    val durationMin = (item.readDuration / 60000).coerceAtLeast(1)
                    Text(
                        text = "Read for ~$durationMin min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            // Actions
            FilledTonalButton(
                onClick = onRead,
                modifier = Modifier.testTag("history-resume-${item.chapterId}"),
            ) {
                Text("Resume")
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.testTag("history-delete-${item.chapterId}"),
            ) {
                Text("Delete")
            }
        }
    }
}
