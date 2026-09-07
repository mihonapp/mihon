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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.track.DesktopTracker
import mihon.desktop.track.TrackerAuthType

@Composable
fun TrackerLoginDialog(
    tracker: DesktopTracker,
    onDismiss: () -> Unit,
    onLogin: (credentials: Map<String, String>) -> Unit,
) {
    val strings = LocalStrings.current
    var username by remember { mutableStateOf(tracker.username.orEmpty()) }
    var passwordOrToken by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(tracker.serverUrl.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            Text(
                                text = strings.trackerAuthUrl(url),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val creds = mutableMapOf<String, String>()
                    if (username.isNotBlank()) creds["username"] = username.trim()
                    if (passwordOrToken.isNotBlank()) {
                        creds["password"] = passwordOrToken.trim()
                        creds["token"] = passwordOrToken.trim()
                    }
                    if (serverUrl.isNotBlank()) {
                        creds["server_url"] = serverUrl.trim()
                        creds["url"] = serverUrl.trim()
                    }
                    onLogin(creds)
                },
                modifier = Modifier.testTag("tracker-login-submit"),
            ) {
                Text(if (tracker.authType == TrackerAuthType.SERVER) strings.trackerConnect else strings.trackerLogin)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("tracker-login-cancel"),
            ) {
                Text(strings.dialogCancel)
            }
        },
    )
}
