package mihon.feature.protectedfolder.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import mihon.domain.protectedfolder.service.ProtectedFolderPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * What has to be typed before the protected folder is wiped. Not translated, so the dialog can name it exactly.
 */
private const val WIPE_CONFIRMATION_WORD = "DELETE"

@Composable
fun ProtectedFolderForgotPinDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == WIPE_CONFIRMATION_WORD) {
                Text(
                    text = stringResource(MR.strings.protected_folder_forgot_pin_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        title = {
            Text(text = stringResource(MR.strings.protected_folder_forgot_pin_title))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = stringResource(MR.strings.protected_folder_forgot_pin_message, WIPE_CONFIRMATION_WORD))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.medium),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
    )
}

@Composable
fun ProtectedFolderConfirmDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = { Text(text = title) },
        text = { Text(text = message) },
    )
}

/**
 * Options that would let someone get at the folder's contents, so they are only offered once it is open.
 */
@Composable
fun ProtectedFolderOptionsDialog(
    preferences: ProtectedFolderPreferences,
    biometricsSupported: Boolean,
    onDismissRequest: () -> Unit,
) {
    val secureScreen by preferences.secureScreen.collectAsState()
    val useBiometrics by preferences.useBiometrics.collectAsState()
    val includeInBackups by preferences.includeInBackups.collectAsState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = { Text(text = stringResource(MR.strings.protected_folder_options)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LabeledCheckbox(
                    label = stringResource(MR.strings.protected_folder_secure_screen),
                    checked = secureScreen,
                    onCheckedChange = preferences.secureScreen::set,
                )
                if (biometricsSupported) {
                    LabeledCheckbox(
                        label = stringResource(MR.strings.protected_folder_biometrics),
                        checked = useBiometrics,
                        onCheckedChange = preferences.useBiometrics::set,
                    )
                }
                LabeledCheckbox(
                    label = stringResource(MR.strings.protected_folder_backup),
                    checked = includeInBackups,
                    onCheckedChange = preferences.includeInBackups::set,
                )
                Text(
                    text = stringResource(MR.strings.protected_folder_backup_summary),
                    modifier = Modifier.padding(top = MaterialTheme.padding.small),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
