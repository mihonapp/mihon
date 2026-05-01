package eu.kanade.presentation.translation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TranslationSetupRequiredDialog(
    message: String,
    onDismissRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.translation_setup_required)) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(MR.strings.translation_setup_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
