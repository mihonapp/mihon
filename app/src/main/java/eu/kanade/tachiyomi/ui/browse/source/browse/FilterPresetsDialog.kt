package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.FilterPreset
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun FilterPresetsDialog(
    onDismissRequest: () -> Unit,
    presets: List<FilterPreset>,
    currentFilters: FilterList,
    autoApplyEnabled: Boolean,
    onSavePreset: (name: String, setAsDefault: Boolean) -> Unit,
    onLoadPreset: (Long) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onSetDefaultPreset: (Long?) -> Unit,
    onToggleAutoApply: (Boolean) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
        ) {
            item {
                Text(
                    text = "Filter Presets",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = autoApplyEnabled,
                        onCheckedChange = onToggleAutoApply,
                    )
                    Text(
                        text = "Auto-apply default preset when opening source",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text("Save Current Filters as Preset")
                }
            }

            items(presets) { preset ->
                PresetItem(
                    preset = preset,
                    onLoad = { onLoadPreset(preset.id) },
                    onDelete = { onDeletePreset(preset.id) },
                    onSetDefault = {
                        val newDefaultId = if (preset.isDefault) null else preset.id
                        onSetDefaultPreset(newDefaultId)
                    },
                )
            }

            if (presets.isEmpty()) {
                item {
                    Text(
                        text = "No saved presets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name, setAsDefault ->
                onSavePreset(name, setAsDefault)
                showSaveDialog = false
            },
        )
    }
}

@Composable
private fun PresetItem(
    preset: FilterPreset,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (preset.isDefault) {
                Text(
                    text = "Default preset",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row {
            IconButton(onClick = onSetDefault) {
                Icon(
                    imageVector = if (preset.isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (preset.isDefault) "Remove default" else "Set as default",
                    tint = if (preset.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete preset",
                )
            }
        }
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var presetName by remember { mutableStateOf("") }
    var setAsDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Filter Preset") },
        text = {
            Column {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it },
                    )
                    Text(
                        text = "Set as default preset",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(presetName, setAsDefault) },
                enabled = presetName.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
