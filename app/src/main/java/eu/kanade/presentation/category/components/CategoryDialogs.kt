// Adapted from https://github.com/komikku-app/komikku/pull/1356
// Copyright Komikku contributors, Apache-2.0

package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.asToggleableState
import eu.kanade.presentation.category.buildCategoryAncestorIds
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.visibleCategoryHierarchy
import eu.kanade.presentation.category.visualName
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String, Long?) -> Unit,
    categories: List<String>,
    parentOptions: List<Category> = emptyList(),
) {
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<Long?>(null) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name, categories) { categories.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onCreate(name, parentId)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_add_category))
        },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(text = stringResource(MR.strings.name))
                    },
                    supportingText = {
                        val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                            MR.strings.error_category_exists
                        } else {
                            MR.strings.information_required_plain
                        }
                        Text(text = stringResource(msgRes))
                    },
                    isError = name.isNotEmpty() && nameAlreadyExists,
                    singleLine = true,
                )
                ParentCategorySelector(
                    parentOptions = parentOptions,
                    selectedParentId = parentId,
                    onSelectParent = { parentId = it },
                )
            }
        },
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String, Long?) -> Unit,
    categories: List<String>,
    category: String,
    parentOptions: List<Category> = emptyList(),
    initialParentId: Long? = null,
) {
    var name by remember { mutableStateOf(category) }
    val valueHasChanged = name != category
    var parentId by remember { mutableStateOf(initialParentId) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name, categories, category) { categories.contains(name) && name != category }
    val parentHasChanged = parentId != initialParentId
    val hasChanges = name.isNotEmpty() && !nameAlreadyExists && (valueHasChanged || parentHasChanged)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = hasChanges,
                onClick = {
                    onRename(name, parentId)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_rename_category))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(MR.strings.name)) },
                    supportingText = {
                        val msgRes = if (valueHasChanged && nameAlreadyExists) {
                            MR.strings.error_category_exists
                        } else {
                            MR.strings.information_required_plain
                        }
                        Text(text = stringResource(msgRes))
                    },
                    isError = valueHasChanged && nameAlreadyExists,
                    singleLine = true,
                )
                ParentCategorySelector(
                    parentOptions = parentOptions,
                    selectedParentId = parentId,
                    onSelectParent = { parentId = it },
                )
            }
        },
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    category: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.delete_category))
        },
        text = {
            Text(text = stringResource(MR.strings.delete_category_confirmation, category))
        },
    )
}

@Composable
private fun ParentCategorySelector(
    parentOptions: List<Category>,
    selectedParentId: Long?,
    onSelectParent: (Long?) -> Unit,
) {
    if (parentOptions.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(MR.strings.none)
    val selectedCategory = remember(selectedParentId, parentOptions) {
        parentOptions.firstOrNull { it.id == selectedParentId }
    }
    val selectedLabel = if (selectedCategory != null) selectedCategory.visualName else noneLabel

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.padding.small),
    ) {
        Text(
            text = stringResource(MR.strings.parent_category),
            style = MaterialTheme.typography.labelMedium,
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.none)) },
                    onClick = {
                        onSelectParent(null)
                        expanded = false
                    },
                )
                parentOptions.forEach { parent ->
                    DropdownMenuItem(
                        text = { Text(parent.visualName) },
                        onClick = {
                            onSelectParent(parent.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ChangeCategoryDialog(
    initialSelection: List<CheckboxState<Category>>,
    onDismissRequest: () -> Unit,
    onEditCategories: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    if (initialSelection.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onEditCategories()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_edit_categories))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_move_category))
            },
            text = {
                Text(text = stringResource(MR.strings.information_empty_category_dialog))
            },
        )
        return
    }
    var selection by remember { mutableStateOf(initialSelection) }
    val initiallySelectedCategoryIds = remember(initialSelection) {
        initialSelection.mapNotNull { checkbox ->
            checkbox.value.id.takeIf {
                checkbox !is CheckboxState.State.None && checkbox !is CheckboxState.TriState.None
            }
        }
    }
    var expandedParents by rememberSaveable {
        mutableStateOf(
            buildCategoryAncestorIds(
                categories = initialSelection.map { it.value },
                categoryIds = initiallySelectedCategoryIds,
            ),
        )
    }

    val hierarchy = remember(initialSelection) {
        buildCategoryHierarchy(initialSelection.map { it.value })
    }
    val categoriesWithChildren = remember(initialSelection) {
        initialSelection.mapNotNullTo(mutableSetOf()) { it.value.parentId }
    }
    val orderedSelection = remember(selection, expandedParents, hierarchy) {
        val selectionMap = selection.associateBy { it.value.id }
        visibleCategoryHierarchy(hierarchy, expandedParents)
            .mapNotNull { entry ->
                selectionMap[entry.category.id]?.let { CheckboxEntry(it, entry.depth) }
            }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row {
                tachiyomi.presentation.core.components.material.TextButton(onClick = {
                    onDismissRequest()
                    onEditCategories()
                }) {
                    Text(text = stringResource(MR.strings.action_edit))
                }
                Spacer(modifier = Modifier.weight(1f))
                tachiyomi.presentation.core.components.material.TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onConfirm(
                            selection
                                .filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }
                                .map { it.value.id },
                            selection
                                .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
                                .map { it.value.id },
                        )
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_move_category))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                orderedSelection.forEach { entry ->
                    val checkbox = entry.checkbox
                    val isExpanded = expandedParents.contains(checkbox.value.id)
                    val hasChildren = checkbox.value.id in categoriesWithChildren
                    val onChange: (CheckboxState<Category>) -> Unit = {
                        val index = selection.indexOf(it)
                        if (index != -1) {
                            val mutableList = selection.toMutableList()
                            mutableList[index] = it.next()
                            selection = mutableList.toList()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChange(checkbox) }
                            .padding(start = MaterialTheme.padding.medium * entry.depth.coerceAtLeast(0).toFloat()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (checkbox) {
                            is CheckboxState.TriState -> {
                                TriStateCheckbox(
                                    state = checkbox.asToggleableState(),
                                    onClick = { onChange(checkbox) },
                                )
                            }
                            is CheckboxState.State -> {
                                Checkbox(
                                    checked = checkbox.isChecked,
                                    onCheckedChange = { onChange(checkbox) },
                                )
                            }
                        }

                        Text(
                            text = checkbox.value.visualName,
                            modifier = Modifier
                                .padding(horizontal = MaterialTheme.padding.medium)
                                .weight(1f),
                        )

                        if (hasChildren) {
                            CategoryExpandButton(isExpanded) {
                                expandedParents =
                                    if (isExpanded) {
                                        expandedParents - checkbox.value.id
                                    } else {
                                        expandedParents +
                                            checkbox.value.id
                                    }
                            }
                        }
                    }
                }
            }
        },
    )
}

private data class CheckboxEntry(
    val checkbox: CheckboxState<Category>,
    val depth: Int,
)
