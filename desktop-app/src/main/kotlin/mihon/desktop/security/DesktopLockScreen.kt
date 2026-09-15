package mihon.desktop.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

const val DESKTOP_LOCK_SCREEN_TEST_TAG = "desktop-lock-screen"
const val DESKTOP_LOCK_PIN_FIELD_TEST_TAG = "desktop-lock-pin-field"
const val DESKTOP_LOCK_UNLOCK_BUTTON_TEST_TAG = "desktop-lock-unlock-button"
const val DESKTOP_LOCK_ERROR_TEST_TAG = "desktop-lock-error"
const val DESKTOP_LOCK_FORGOT_PIN_BUTTON_TEST_TAG = "desktop-lock-forgot-pin-button"
const val DESKTOP_LOCK_FORGOT_CONFIRM_BUTTON_TEST_TAG = "desktop-lock-forgot-confirm-button"
const val DESKTOP_LOCK_FORGOT_CANCEL_BUTTON_TEST_TAG = "desktop-lock-forgot-cancel-button"

/**
 * Full-window lock screen. The parent (see [DesktopAppLockGate]) only composes this when the app is
 * locked, so no destination content or shell chrome is visible or interactive behind it.
 */
@Composable
fun DesktopLockScreen(
    onUnlock: (String) -> Boolean,
    onForgotPinConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    appName: String = "mihondesk",
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgotConfirmation by remember { mutableStateOf(false) }

    fun submitPin() {
        if (pin.isBlank()) {
            error = "Enter your PIN to unlock."
            return
        }
        if (onUnlock(pin)) {
            pin = ""
            error = null
        } else {
            pin = ""
            error = "Incorrect PIN. Try again."
        }
    }

    Surface(
        modifier = modifier.fillMaxSize().testTag(DESKTOP_LOCK_SCREEN_TEST_TAG),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.height(48.dp).width(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$appName is locked",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your PIN to view your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it
                    error = null
                },
                label = { Text("PIN") },
                singleLine = true,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submitPin() }),
                modifier = Modifier.width(280.dp).testTag(DESKTOP_LOCK_PIN_FIELD_TEST_TAG),
            )
            error?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(DESKTOP_LOCK_ERROR_TEST_TAG),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { submitPin() },
                modifier = Modifier.testTag(DESKTOP_LOCK_UNLOCK_BUTTON_TEST_TAG),
            ) {
                Text("Unlock")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { showForgotConfirmation = true },
                modifier = Modifier.testTag(DESKTOP_LOCK_FORGOT_PIN_BUTTON_TEST_TAG),
            ) {
                Text("Forgot PIN?")
            }
        }
    }

    if (showForgotConfirmation) {
        AlertDialog(
            onDismissRequest = { showForgotConfirmation = false },
            title = { Text("Disable app lock?") },
            text = {
                Text(
                    "If you forgot your PIN you can disable app lock. Your library, downloads " +
                        "and reading history are not deleted. You will need to set a new PIN to " +
                        "lock the app again.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgotConfirmation = false
                        onForgotPinConfirmed()
                    },
                    modifier = Modifier.testTag(DESKTOP_LOCK_FORGOT_CONFIRM_BUTTON_TEST_TAG),
                ) {
                    Text("Disable lock")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotConfirmation = false },
                    modifier = Modifier.testTag(DESKTOP_LOCK_FORGOT_CANCEL_BUTTON_TEST_TAG),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Renders the lock screen instead of [content] while locked. Using a gate rather than a visual
 * overlay guarantees destination content and shell chrome are neither visible nor focusable.
 */
@Composable
fun DesktopAppLockGate(
    isLocked: Boolean,
    onUnlock: (String) -> Boolean,
    onForgotPinConfirmed: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isLocked) {
        DesktopLockScreen(
            onUnlock = onUnlock,
            onForgotPinConfirmed = onForgotPinConfirmed,
        )
    } else {
        content()
    }
}
