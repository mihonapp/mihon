package mihon.desktop.ui.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.track.DesktopTracker
import mihon.desktop.track.TrackerAuthType

@Composable
fun TrackerLoginDialog(
    tracker: DesktopTracker,
    onDismiss: () -> Unit,
    onLogin: suspend (credentials: Map<String, String>) -> Boolean,
) {
    val strings = LocalStrings.current
    var username by remember { mutableStateOf(tracker.username.orEmpty()) }
    var passwordOrToken by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(tracker.serverUrl.orEmpty()) }
    var submitting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Text(
                when (tracker.authType) {
                    TrackerAuthType.SERVER -> strings.trackerConnectTitle(tracker.name)
                    TrackerAuthType.TOKEN -> strings.trackerAuthTitle(tracker.name)
                    TrackerAuthType.CREDENTIALS -> strings.trackerLoginTitle(tracker.name)
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("tracker-login-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tracker.authType) {
                    TrackerAuthType.SERVER -> {
                        Text(strings.trackerServerAuthHelp, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text(strings.trackerServerUrl) },
                            modifier = Modifier.fillMaxWidth().testTag("tracker-login-server-url"),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(strings.trackerUsername) },
                            modifier = Modifier.fillMaxWidth().testTag("tracker-login-username"),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = passwordOrToken,
                            onValueChange = { passwordOrToken = it },
                            label = { Text(strings.trackerPasswordOrApiKey) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("tracker-login-password"),
                            singleLine = true,
                        )
                    }
                    TrackerAuthType.TOKEN -> {
                        tracker.authUrl?.let { url ->
                            TextButton(onClick = { uriHandler.openUri(url) }) {
                                Text(strings.trackerAuthTitle(tracker.name))
                            }
                        }
                        OutlinedTextField(
                            value = passwordOrToken,
                            onValueChange = { passwordOrToken = it },
                            label = { Text(strings.trackerToken) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("tracker-login-token"),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(strings.trackerAccountName) },
                            modifier = Modifier.fillMaxWidth().testTag("tracker-login-username"),
                            singleLine = true,
                        )
                    }
                    TrackerAuthType.CREDENTIALS -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(strings.trackerUsername) },
                            modifier = Modifier.fillMaxWidth().testTag("tracker-login-username"),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = passwordOrToken,
                            onValueChange = { passwordOrToken = it },
                            label = { Text(strings.trackerPassword) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("tracker-login-password"),
                            singleLine = true,
                        )
                    }
                }
                if (failed) {
                    Text(
                        strings.trackerLoginFailed,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("tracker-login-error"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    failed = false
                    val creds = trackerLoginCredentials(tracker.authType, username, passwordOrToken, serverUrl)
                    scope.launch {
                        try {
                            if (onLogin(creds)) onDismiss() else failed = true
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            submitting = false
                        }
                    }
                },
                enabled = !submitting,
                modifier = Modifier.testTag("tracker-login-submit"),
            ) {
                Text(if (tracker.authType == TrackerAuthType.SERVER) strings.trackerConnect else strings.trackerLogin)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !submitting,
                modifier = Modifier.testTag("tracker-login-cancel"),
            ) {
                Text(strings.dialogCancel)
            }
        },
    )
}

internal fun trackerLoginCredentials(
    authType: TrackerAuthType,
    username: String,
    secret: String,
    serverUrl: String,
): Map<String, String> = buildMap {
    if (username.isNotBlank()) put("username", username.trim())
    when (authType) {
        TrackerAuthType.CREDENTIALS -> put("password", secret)
        TrackerAuthType.TOKEN -> put("token", secret.trim())
        TrackerAuthType.SERVER -> {
            put("server_url", serverUrl.trim())
            if (username.isNotBlank()) put("password", secret) else put("token", secret.trim())
        }
    }
}
