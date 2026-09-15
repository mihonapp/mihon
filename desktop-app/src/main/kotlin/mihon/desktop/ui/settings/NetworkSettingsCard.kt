package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.DesktopNetworkSettingsStore
import mihon.desktop.extension.DesktopProxyMode
import mihon.desktop.i18n.recoveryText
import mihon.desktop.preferences.DesktopPreferenceStore

@Composable
fun NetworkSettingsCard(preferences: DesktopPreferenceStore) {
    val store = remember(preferences) { DesktopNetworkSettingsStore(preferences) }
    var policy by remember(store) { mutableStateOf(store.load()) }
    var port by remember { mutableStateOf(policy.proxyPort.toString()) }
    var connectTimeout by remember { mutableStateOf(policy.connectTimeoutSeconds.toString()) }
    var readTimeout by remember { mutableStateOf(policy.readTimeoutSeconds.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().testTag("network-settings-card")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(recoveryText("Source network", "图源网络", "圖源網路"), style = MaterialTheme.typography.titleMedium)
            Text(
                recoveryText(
                    "Changes apply to new requests. Site-specific headers take precedence.",
                    "保存后对新请求生效，图源专用请求头优先。",
                    "儲存後對新請求生效，圖源專用請求標頭優先。",
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopProxyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = policy.proxyMode == mode,
                        onClick = {
                            policy = policy.copy(proxyMode = mode)
                            saved = false
                        },
                        label = {
                            Text(
                                when (mode) {
                                    DesktopProxyMode.SYSTEM -> recoveryText("System", "系统代理", "系統代理")
                                    DesktopProxyMode.DIRECT -> recoveryText("Direct", "直连", "直接連線")
                                    DesktopProxyMode.HTTP -> "HTTP"
                                    DesktopProxyMode.SOCKS -> "SOCKS"
                                },
                            )
                        },
                    )
                }
            }
            if (policy.proxyMode == DesktopProxyMode.HTTP || policy.proxyMode == DesktopProxyMode.SOCKS) {
                OutlinedTextField(
                    value = policy.proxyHost,
                    onValueChange = {
                        policy = policy.copy(proxyHost = it)
                        saved = false
                    },
                    label = { Text(recoveryText("Proxy hostname", "代理主机", "代理主機")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        port = it
                        saved = false
                    },
                    label = { Text(recoveryText("Port", "端口", "連接埠")) },
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = connectTimeout,
                    onValueChange = {
                        connectTimeout = it
                        saved = false
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(recoveryText("Connect timeout (s)", "连接超时（秒）", "連線逾時（秒）")) },
                )
                OutlinedTextField(
                    value = readTimeout,
                    onValueChange = {
                        readTimeout = it
                        saved = false
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(recoveryText("Read timeout (s)", "读取超时（秒）", "讀取逾時（秒）")) },
                )
            }
            OutlinedTextField(
                value = policy.userAgent,
                onValueChange = {
                    policy = policy.copy(userAgent = it)
                    saved = false
                },
                label = { Text("User-Agent") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved) Text(recoveryText("Saved", "已保存", "已儲存"))
            Button(onClick = {
                error = null
                try {
                    val updated = policy.copy(
                        proxyPort = port.toIntOrNull() ?: 0,
                        connectTimeoutSeconds = connectTimeout.toIntOrNull() ?: 0,
                        readTimeoutSeconds = readTimeout.toIntOrNull() ?: 0,
                    )
                    store.save(updated)
                    policy = updated
                    saved = true
                } catch (failure: Exception) {
                    error = failure.message
                }
            }) { Text(recoveryText("Save network settings", "保存网络设置", "儲存網路設定")) }
        }
    }
}
