package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.platform.WindowsBackgroundScheduler
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import java.util.Locale

@Composable
internal fun BackgroundSettingsCard(
    preferences: DesktopPreferenceStore,
    scheduler: WindowsBackgroundScheduler?,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    var current by remember { mutableStateOf(preferences.load()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(scheduler?.lastFailure) }
    val scope = rememberCoroutineScope()
    val language = current.language.let {
        if (it != AppLanguage.System) {
            it
        } else if (Locale.getDefault().language == "zh") {
            if (Locale.getDefault().country in setOf("TW", "HK", "MO")) {
                AppLanguage.TraditionalChinese
            } else {
                AppLanguage.SimplifiedChinese
            }
        } else {
            AppLanguage.English
        }
    }
    fun label(english: String, simplified: String, traditional: String) = when (language) {
        AppLanguage.SimplifiedChinese -> simplified
        AppLanguage.TraditionalChinese -> traditional
        else -> english
    }
    Card(Modifier.fillMaxWidth().testTag("background-tasks-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label("Run updates and backups while the app is closed", "关闭应用后继续更新与备份", "關閉應用程式後繼續更新與備份"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = current.backgroundTasksEnabled,
                    enabled = !busy && scheduler != null,
                    modifier = Modifier.testTag("background-tasks-switch"),
                    onCheckedChange = { enabled ->
                        val original = preferences.load()
                        if (enabled && original.libraryUpdateIntervalHours <= 0 && original.backupIntervalHours <= 0) {
                            message = label(
                                "Set an update or backup interval first; both are currently off.",
                                "请先设置书库更新或自动备份周期，当前两项均已关闭。",
                                "請先設定書庫更新或自動備份週期，目前兩項均已關閉。",
                            )
                        } else {
                            busy = true
                            scope.launch {
                                try {
                                    val updated = withContext(Dispatchers.IO + NonCancellable) {
                                        val results = requireNotNull(scheduler).reconcile(
                                            enabled,
                                            original.libraryUpdateIntervalHours,
                                            original.backupIntervalHours,
                                        )
                                        check(results.values.all { it.exitCode == 0 }) {
                                            results.values.filter { it.exitCode != 0 }.joinToString("\n") { it.output }
                                        }
                                        preferences.load().copy(
                                            backgroundTasksEnabled = enabled,
                                        ).also(preferences::save)
                                    }
                                    current = updated
                                    onPreferencesChanged?.invoke(updated)
                                    message = if (enabled) {
                                        label("Background tasks enabled.", "后台任务已启用。", "背景工作已啟用。")
                                    } else {
                                        label("Background tasks removed.", "后台任务已移除。", "背景工作已移除。")
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    withContext(Dispatchers.IO + NonCancellable) {
                                        runCatching {
                                            scheduler?.reconcile(
                                                original.backgroundTasksEnabled,
                                                original.libraryUpdateIntervalHours,
                                                original.backupIntervalHours,
                                            )
                                        }
                                    }
                                    message = error.message
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                )
            }
            Text(
                label(
                    "Uses your existing library update and backup intervals. Windows must stay signed in; no password is stored.",
                    "沿用书库更新和自动备份周期。Windows 用户需保持登录，不保存账户密码。",
                    "沿用書庫更新和自動備份週期。Windows 使用者需保持登入，不儲存帳戶密碼。",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (scheduler == null) {
                Text(
                    label(
                        "Available in the packaged mihondesk application.",
                        "请在已打包的 mihondesk 应用中设置。",
                        "請在已封裝的 mihondesk 應用程式中設定。",
                    ),
                )
            }
            message?.let { Text(it, modifier = Modifier.testTag("background-tasks-message")) }
        }
    }
}
