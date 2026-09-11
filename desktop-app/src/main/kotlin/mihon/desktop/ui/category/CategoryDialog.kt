package mihon.desktop.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import mihon.desktop.category.DesktopCategory
import mihon.desktop.i18n.LocalStrings

@Composable
fun ManageCategoriesDialog(
    categories: List<DesktopCategory>,
    onDismiss: () -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (Long, String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onMoveCategory: (DesktopCategory, Int) -> Unit = { _, _ -> },
) {
    val strings = LocalStrings.current
    var newCategoryName by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<DesktopCategory?>(null) }
    var renameValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.categoryManageTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("manage-categories-dialog"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Add new category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        modifier = Modifier.weight(1f).testTag("create-category-input"),
                        label = { Text(strings.categoryNewNameLabel) },
                        singleLine = true,
                    )
                    FilledTonalButton(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                onCreateCategory(newCategoryName.trim())
                                newCategoryName = ""
                            }
                        },
                        modifier = Modifier.testTag("create-category-button"),
                    ) {
                        Text(strings.categoryAdd)
                    }
                }

                // Existing categories list
                if (categories.isEmpty()) {
                    Text(
                        text = strings.categoryEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(categories, key = { _, cat -> cat.id }) { index, cat ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onMoveCategory(cat, index - 1) },
                                        enabled = index > 0,
                                        modifier = Modifier.testTag("move-category-up-${cat.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = null,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveCategory(cat, index + 1) },
                                        enabled = index < categories.lastIndex,
                                        modifier = Modifier.testTag("move-category-down-${cat.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            editingCategory = cat
                                            renameValue = cat.name
                                        },
                                        modifier = Modifier.testTag("rename-category-${cat.id}"),
                                    ) {
                                        Text(strings.categoryRename)
                                    }
                                    TextButton(
                                        onClick = { onDeleteCategory(cat.id) },
                                        modifier = Modifier.testTag("delete-category-${cat.id}"),
                                    ) {
                                        Text(strings.categoryDelete, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close-manage-categories-button")) {
                Text(strings.dialogDone)
            }
        },
    )

    editingCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text(strings.categoryRenameTitle) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    modifier = Modifier.fillMaxWidth().testTag("rename-category-input"),
                    label = { Text(strings.categoryNameLabel) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            onRenameCategory(target.id, renameValue.trim())
                            editingCategory = null
                        }
                    },
                    modifier = Modifier.testTag("confirm-rename-category-button"),
                ) {
                    Text(strings.categorySave)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }
}

@Composable
fun EditMangaCategoriesDialog(
    allCategories: List<DesktopCategory>,
    assignedCategoryIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit,
) {
    val strings = LocalStrings.current
    var selectedIds by remember { mutableStateOf(assignedCategoryIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.categorySetTitle) },
        text = {
            if (allCategories.isEmpty()) {
                Text(strings.categoryNoneExist)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(240.dp).testTag("edit-manga-categories-dialog"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(allCategories, key = { it.id }) { cat ->
                        val checked = cat.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - cat.id else selectedIds + cat.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedIds = if (isChecked) selectedIds + cat.id else selectedIds - cat.id
                                },
                                modifier = Modifier.testTag("manga-category-checkbox-${cat.id}"),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(selectedIds.toList())
                    onDismiss()
                },
                modifier = Modifier.testTag("save-manga-categories-button"),
            ) {
                Text(strings.categorySave)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.dialogCancel)
            }
        },
    )
}
