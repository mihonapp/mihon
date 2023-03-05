package eu.kanade.presentation.history.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

@Composable
fun HistoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: (Boolean) -> Unit,
) {
    var removeEverything by remember { mutableStateOf(false) }

    AlertDialog(
        title = {
            Text(text = stringResource(R.string.action_remove))
        },
        text = {
            Column {
                Text(text = stringResource(R.string.dialog_with_checkbox_remove_description))
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .toggleable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            value = removeEverything,
                            onValueChange = { removeEverything = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = removeEverything,
                        onCheckedChange = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.dialog_with_checkbox_reset),
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete(removeEverything)
                onDismissRequest()
            },) {
                Text(text = stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun HistoryDeleteAllDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.action_remove_everything))
        },
        text = {
            Text(text = stringResource(R.string.clear_history_confirmation))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            },) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
