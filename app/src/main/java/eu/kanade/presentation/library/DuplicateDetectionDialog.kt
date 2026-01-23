package eu.kanade.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryScreenModel

@Composable
fun DuplicateDetectionDialog(
    duplicates: List<LibraryScreenModel.DuplicateGroup>,
    onDismissRequest: () -> Unit,
    onSelectAllExceptFirst: () -> Unit,
    onSelectAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("Potential Duplicates")
        },
        text = {
            if (duplicates.isEmpty()) {
                Text("No potential duplicates found in your library.")
            } else {
                Column {
                    Text(
                        text = "Found ${duplicates.size} group(s) with similar titles:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(duplicates) { group ->
                            DuplicateGroupItem(group)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (duplicates.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSelectAllExceptFirst) {
                        Text("Select All Except First")
                    }
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun DuplicateGroupItem(group: LibraryScreenModel.DuplicateGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        group.manga.forEachIndexed { index, manga ->
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                color = if (index == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(start = (index * 8).dp),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
