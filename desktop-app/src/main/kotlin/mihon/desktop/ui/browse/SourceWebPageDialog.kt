package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.i18n.recoveryText
import mihon.desktop.webview.DesktopWebViewManager
import mihon.extension.model.SourceDescriptor

@Composable
fun SourceWebPageDialog(runtime: DesktopRuntime, source: SourceDescriptor, onDismiss: () -> Unit, onRetry: () -> Unit) {
    var session by remember(source.id) { mutableStateOf<DesktopWebViewManager.BrowserSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source.id) {
        var owned: DesktopWebViewManager.BrowserSession? = null
        try {
            val url = runtime.sourceManager.sourceWebPage(source.id)
                ?: error("This source does not provide a web address")
            val owner = runtime.sourceManager.getSourceStates().firstOrNull { it.source.id == source.id }
                ?.extensionPackage ?: "builtin"
            owned = runtime.webViews?.open(source.id, owner, url) ?: error("Browser component is unavailable")
            session = owned
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { owned?.close() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.name) },
        text = {
            Column(
                modifier = Modifier.testTag("source-web-page-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    recoveryText(
                        "Complete sign-in or verification in the opened source window, then return here to retry.",
                        "请在已打开的图源窗口中完成登录或网页验证，然后返回此处重试。",
                        "請在已開啟的圖源視窗中完成登入或網頁驗證，然後返回此處重試。",
                    ),
                )
                val current = session
                if (current == null &&
                    error == null
                ) {
                    Text(recoveryText("Opening source page…", "正在打开图源网页…", "正在開啟圖源網頁…"))
                }
                current?.let { browser ->
                    val state by browser.state.collectAsState()
                    if (state.type ==
                        "closed"
                    ) {
                        Text(
                            recoveryText(
                                "The source window was closed. Reopen it if verification is unfinished.",
                                "图源窗口已关闭。如尚未完成验证，请重新打开。",
                                "圖源視窗已關閉。如尚未完成驗證，請重新開啟。",
                            ),
                        )
                    }
                    if (state.type == "error") Text(state.value, color = MaterialTheme.colorScheme.error)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = session != null && !saving, onClick = {
                scope.launch {
                    saving = true
                    try {
                        session?.exportCookies()
                        onDismiss()
                        onRetry()
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (
                        failure: Exception,
                    ) {
                        error = failure.message
                    } finally {
                        saving = false
                    }
                }
            }) { Text(recoveryText("Done, retry", "完成并重试", "完成並重試")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(recoveryText("Cancel", "取消")) } },
    )
}
