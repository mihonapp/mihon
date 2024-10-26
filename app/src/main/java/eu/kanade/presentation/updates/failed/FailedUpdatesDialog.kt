package eu.kanade.presentation.updates.failed

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R

@Composable
fun ErrorMessageDialog(
    onDismissRequest: () -> Unit,
    onCopyClick: () -> Unit,
    errorMessage: String,
) {
    AlertDialog(
        text = {
            Column {
                Text(
                    text = "${stringResource(R.string.label_error_message)}:\n",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Justify,
                )
                Text(text = errorMessage)
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onCopyClick()
                onDismissRequest()
            },) {
                Text(text = stringResource(R.string.copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
