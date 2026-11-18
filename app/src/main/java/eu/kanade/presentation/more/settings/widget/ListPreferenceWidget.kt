package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A text field shown under the entry with [key] while it is selected.
 */
class ListPreferenceInput(
    val key: Any?,
    val value: String,
    val label: String,
    val error: String,
    val isValid: (String) -> Boolean,
    val onValueChange: (String) -> Unit,
)

@Composable
fun <T> ListPreferenceWidget(
    value: T,
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    entries: Map<out T, String>,
    onValueChange: (T) -> Unit,
    input: ListPreferenceInput? = null,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        ListPreferenceDialog(
            title = title,
            value = value,
            entries = entries,
            onValueChange = onValueChange,
            onDismissRequest = { isDialogShown = false },
            input = input,
        )
    }
}

@Composable
fun <T> ListPreferenceDialog(
    title: String?,
    value: T,
    entries: Map<out T, String>,
    onValueChange: (T) -> Unit,
    onDismissRequest: () -> Unit,
    message: String? = null,
    input: ListPreferenceInput? = null,
) {
    var text by remember { mutableStateOf(input?.value.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title?.let { { Text(text = it) } },
        text = {
            Column {
                if (message != null) PreferenceDialogMessage(message)
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    val state = rememberLazyListState()
                    ScrollbarLazyColumn(state = state) {
                        entries.forEach { current ->
                            val isSelected = value == current.key
                            item {
                                DialogRow(
                                    label = current.value,
                                    isSelected = isSelected,
                                    onSelected = {
                                        onValueChange(current.key)
                                        if (input == null || current.key != input.key) onDismissRequest()
                                    },
                                )
                            }
                            if (input != null && current.key == input.key && isSelected) {
                                item {
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = {
                                            text = it
                                            input.onValueChange(it)
                                        },
                                        label = { Text(text = input.label) },
                                        isError = !input.isValid(text),
                                        supportingText = if (!input.isValid(text)) {
                                            { Text(text = input.error) }
                                        } else {
                                            null
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (state.canScrollBackward) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                    if (state.canScrollForward) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
internal fun PreferenceDialogMessage(message: String) {
    Text(text = message, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DialogRow(
    label: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(
                selected = isSelected,
                onClick = { if (!isSelected) onSelected() },
            )
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.merge(),
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}
