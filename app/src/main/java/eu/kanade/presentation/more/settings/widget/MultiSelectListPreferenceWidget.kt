package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.minimumTouchTargetSize
import eu.kanade.tachiyomi.R

@Composable
fun MultiSelectListPreferenceWidget(
    preference: Preference.PreferenceItem.MultiSelectListPreference,
    values: Set<String>,
    onValuesChange: (Set<String>) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = preference.title,
        subtitle = preference.subtitleProvider(values, preference.entries),
        icon = preference.icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        val selected = remember {
            preference.entries.keys
                .filter { values.contains(it) }
                .toMutableStateList()
        }
        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = preference.title) },
            text = {
                LazyColumn {
                    preference.entries.forEach { current ->
                        item {
                            val isSelected = selected.contains(current.key)
                            val onSelectionChanged = {
                                when (!isSelected) {
                                    true -> selected.add(current.key)
                                    false -> selected.remove(current.key)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { onSelectionChanged() },
                                    )
                                    .minimumTouchTargetSize()
                                    .fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                )
                                Text(
                                    text = current.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 24.dp),
                                )
                            }
                        }
                    }
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        onValuesChange(selected.toMutableSet())
                        isDialogShown = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
