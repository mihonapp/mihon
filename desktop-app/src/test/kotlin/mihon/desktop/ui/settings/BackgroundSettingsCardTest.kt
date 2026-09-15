package mihon.desktop.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.platform.BackgroundProcessResult
import mihon.desktop.platform.BackgroundProcessRunner
import mihon.desktop.platform.WindowsBackgroundScheduler
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

class BackgroundSettingsCardTest {
    @TempDir lateinit var directory: Path

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `background remains off without any configured interval`() = runComposeUiTest {
        val preferences = DesktopPreferenceStore(directory.resolve("prefs.properties"))
        preferences.save(preferences.load().copy(language = AppLanguage.SimplifiedChinese))
        val calls = CopyOnWriteArrayList<List<String>>()
        val scheduler = scheduler(calls)
        setContent { MaterialTheme { BackgroundSettingsCard(preferences, scheduler, null) } }
        onNodeWithTag("background-tasks-switch").performClick()
        onNodeWithText("请先设置书库更新或自动备份周期，当前两项均已关闭。").assertExists()
        assertFalse(preferences.load().backgroundTasksEnabled)
        assertTrue(calls.isEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `explicit enabling persists only after task registration succeeds`() = runComposeUiTest {
        val preferences = DesktopPreferenceStore(directory.resolve("prefs.properties"))
        preferences.save(preferences.load().copy(backupIntervalHours = 24))
        val calls = CopyOnWriteArrayList<List<String>>()
        val scheduler = scheduler(calls)
        setContent { MaterialTheme { BackgroundSettingsCard(preferences, scheduler, null) } }
        assertFalse(preferences.load().backgroundTasksEnabled)
        onNodeWithTag("background-tasks-switch").performClick()
        waitUntil(timeoutMillis = 5_000) { preferences.load().backgroundTasksEnabled }
        assertTrue(calls.any { it.first() == "/Create" })
        assertTrue(preferences.load().backgroundTasksEnabled)
    }

    private fun scheduler(calls: MutableList<List<String>>) = WindowsBackgroundScheduler(
        Files.createFile(directory.resolve("MihonW.exe")),
        directory,
        BackgroundProcessRunner { arguments ->
            calls.add(arguments)
            if (arguments.first() == "/Query") {
                BackgroundProcessResult(1, "The system cannot find the file specified.")
            } else {
                BackgroundProcessResult(0, "ok")
            }
        },
    )
}
