package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.reader.DesktopReaderSettingsStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SettingsScreenTest {

    @TempDir
    lateinit var tempDir: Path

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `settings screen displays sections and switches panes`() = runComposeUiTest {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
        val readerSettingsStore = DesktopReaderSettingsStore(prefStore)

        var exportBackupClicked = false
        var importBackupClicked = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                SettingsScreen(
                    preferenceStore = prefStore,
                    readerSettingsStore = readerSettingsStore,
                    diagnosticService = null,
                    onImportBackup = { importBackupClicked = true },
                    onExportBackup = { exportBackupClicked = true },
                )
            }
        }

        onNodeWithTag("settings-screen").assertExists()
        onNodeWithTag("settings-section-General").assertExists()
        onNodeWithText("Application Info").assertExists()

        // Switch to Appearance
        onNodeWithTag("settings-section-Appearance").performClick()
        onNodeWithText("Theme Mode").assertExists()
        onNodeWithTag("theme-button-Dark").performClick()
        prefStore.load().themeMode shouldBe ThemeMode.Dark

        // Switch to Backup
        onNodeWithTag("settings-section-Backup").performClick()
        onNodeWithText("Cross-Platform Backup Exchange").assertExists()
        onNodeWithTag("settings-export-backup-button").performClick()
        exportBackupClicked shouldBe true
        onNodeWithTag("settings-import-backup-button").performClick()
        importBackupClicked shouldBe true

        // Switch to Advanced
        onNodeWithTag("settings-section-Advanced").performClick()
        onNodeWithText("Database & System Diagnostics").assertExists()
        onNodeWithTag("check-integrity-button").assertExists()
        onNodeWithTag("export-diagnostic-bundle-button").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `about screen displays application info`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                AboutScreen()
            }
        }

        onNodeWithTag("about-screen").assertExists()
        onNodeWithText("About Mihon W").assertExists()
        onNodeWithText("Licensed under the Apache License, Version 2.0.").assertExists()
    }
}
