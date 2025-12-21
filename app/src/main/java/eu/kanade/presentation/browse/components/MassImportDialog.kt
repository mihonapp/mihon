package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.category.model.Category

/**
 * DEPRECATED: Use eu.kanade.presentation.library.components.MassImportDialog instead.
 *
 * Dialog for selecting a category to add found manga to library.
 * This simple category picker has been superseded by the comprehensive MassImportDialog
 * in the library components package, which handles full URL-based imports with progress tracking.
 */
@Deprecated(
    "Use eu.kanade.presentation.library.components.MassImportDialog instead",
    ReplaceWith("eu.kanade.presentation.library.components.MassImportDialog"),
)
@Composable
fun MassImportDialog(
    selectedManga: List<String>,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add ${selectedManga.size} manga to library") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = "Select category:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedCategory == category.id,
                            onClick = { selectedCategory = category.id },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(category.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedCategory)
                    onDismissRequest()
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}
