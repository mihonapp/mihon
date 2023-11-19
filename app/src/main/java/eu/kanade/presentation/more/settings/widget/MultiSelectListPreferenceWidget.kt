package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

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
                            LabeledCheckbox(
                                label = current.value,
                                checked = isSelected,
                                onCheckedChange = {
                                    if (it) {
                                        selected.add(current.key)
                                    } else {
                                        selected.remove(current.key)
                                    }
                                },
                            )
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
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
