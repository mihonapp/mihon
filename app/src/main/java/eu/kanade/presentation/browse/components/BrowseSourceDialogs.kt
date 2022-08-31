package eu.kanade.presentation.browse.components

import androidx.compose.material.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R

@Composable
fun RemoveMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(id = R.string.action_remove))
            }
        },
        title = {
            Text(text = stringResource(id = R.string.are_you_sure))
        },
        text = {
            Text(text = stringResource(R.string.remove_manga))
        },
    )
}
