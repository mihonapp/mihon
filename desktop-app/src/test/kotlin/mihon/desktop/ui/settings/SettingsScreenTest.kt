package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.security.DesktopAppLockController
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `settings screen allows selecting Simplified Chinese and saves preference`() = runComposeUiTest {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
        val readerSettingsStore = DesktopReaderSettingsStore(prefStore)

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                SettingsScreen(
                    preferenceStore = prefStore,
                    readerSettingsStore = readerSettingsStore,
                    diagnosticService = null,
                )
            }
        }

        onNodeWithTag("language-button-SimplifiedChinese").assertExists()
        onNodeWithTag("language-button-SimplifiedChinese").performClick()
        prefStore.load().language shouldBe mihon.desktop.i18n.AppLanguage.SimplifiedChinese
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `about screen renders in Simplified Chinese when Chinese strings are provided`() = runComposeUiTest {
        setContent {
            mihon.desktop.i18n.ProvideDesktopStrings(mihon.desktop.i18n.AppLanguage.SimplifiedChinese) {
                Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                    AboutScreen()
                }
            }
        }

        onNodeWithTag("about-screen").assertExists()
        onNodeWithText("关于 Mihon W").assertExists()
        onNodeWithText("开源许可证").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `settings screen displays downloads and tracking panes and updates preferences`() = runComposeUiTest {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("preferences-down-track.properties"))
        val readerSettingsStore = DesktopReaderSettingsStore(prefStore)
        val trackerStore = mihon.desktop.track.DesktopTrackerStore(prefStore)
        val trackerManager = mihon.desktop.track.DesktopTrackerManager(store = trackerStore)

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                SettingsScreen(
                    preferenceStore = prefStore,
                    readerSettingsStore = readerSettingsStore,
                    diagnosticService = null,
                    trackerManager = trackerManager,
                )
            }
        }

        // Switch to Downloads pane
        onNodeWithTag("settings-section-Downloads").performClick()
        onNodeWithTag("download-storage-input").assertExists()
        onNodeWithTag("parallel-downloads-5").performClick()
        prefStore.load().downloadParallelCount shouldBe 5
        onNodeWithTag("parallel-pages-3").performClick()
        prefStore.load().downloadPageParallelCount shouldBe 3
        onNodeWithTag("download-ahead-2").performClick()
        prefStore.load().downloadAhead shouldBe 2
        onNodeWithTag("delete-downloaded-read-switch").performScrollTo().performClick()
        prefStore.load().deleteDownloadedRead shouldBe true

        // Switch to Tracking pane
        onNodeWithTag("settings-section-Tracking").performClick()
        onNodeWithTag("tracker-setting-card-1").assertExists() // MAL
        onNodeWithTag("tracker-login-1").assertExists()
        onNodeWithTag("tracker-setting-card-2").assertExists() // AniList
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `settings screen allows toggling incognito mode and configuring automated backups`() = runComposeUiTest {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("preferences-incognito-backup.properties"))
        val readerSettingsStore = DesktopReaderSettingsStore(prefStore)

        setContent {
            Box(modifier = Modifier.requiredSize(900.dp, 700.dp)) {
                SettingsScreen(
                    preferenceStore = prefStore,
                    readerSettingsStore = readerSettingsStore,
                )
            }
        }

        // Toggle incognito mode in General pane
        onNodeWithTag("incognito-switch").performScrollTo().performClick()
        prefStore.load().incognitoMode shouldBe true

        // Switch to Backup pane
        onNodeWithTag("settings-section-Backup").performClick()
        onNodeWithTag("backup-interval-24").performScrollTo().performClick()
        prefStore.load().backupIntervalHours shouldBe 24

        onNodeWithTag("backup-retention-5").performScrollTo().performClick()
        prefStore.load().backupRetentionCount shouldBe 5
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `settings screen allows configuring library update and opening cookie manager`() = runComposeUiTest {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("preferences-library-update.properties"))
        val readerSettingsStore = DesktopReaderSettingsStore(prefStore)
        var cookieManagerOpened = false

        setContent {
            Box(modifier = Modifier.requiredSize(900.dp, 700.dp)) {
                SettingsScreen(
                    preferenceStore = prefStore,
                    readerSettingsStore = readerSettingsStore,
                    onOpenCookieManager = { cookieManagerOpened = true },
                )
            }
        }

        // Switch to Library pane
        onNodeWithTag("settings-section-Library").performClick()
        onNodeWithTag("update-interval-12").performClick()
        prefStore.load().libraryUpdateIntervalHours shouldBe 12

        onNodeWithTag("skip-completed-switch").performClick()
        prefStore.load().libraryUpdateSkipCompleted shouldBe false

        onNodeWithTag("skip-started-switch").performScrollTo().performClick()
        prefStore.load().libraryUpdateSkipStarted shouldBe true

        onNodeWithTag("update-include-categories").performScrollTo().performTextInput("2,7")
        prefStore.load().libraryUpdateCategories shouldBe setOf(2L, 7L)

        onNodeWithTag("update-exclude-categories").performScrollTo().performTextInput("9")
        prefStore.load().libraryUpdateCategoriesExclude shouldBe setOf(9L)

        onNodeWithTag("desktop-notifications-hide-content-switch").performScrollTo().performClick()
        prefStore.load().desktopNotificationsHideContent shouldBe true

        onNodeWithTag("auto-download-new-switch").performClick()
        prefStore.load().autoDownloadNewChapters shouldBe true

        // Switch to Advanced pane
        onNodeWithTag("settings-section-Advanced").performClick()
        onNodeWithTag("open-cookie-manager-button").performScrollTo().performClick()
        cookieManagerOpened shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `settings screen configures app lock security controls`() = runComposeUiTest {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("preferences-security.properties"))
        val readerSettingsStore = DesktopReaderSettingsStore(prefStore)
        val appLockController = DesktopAppLockController(prefStore, clock = { 0L })

        setContent {
            Box(modifier = Modifier.requiredSize(900.dp, 800.dp)) {
                SettingsScreen(
                    preferenceStore = prefStore,
                    readerSettingsStore = readerSettingsStore,
                    appLockController = appLockController,
                )
            }
        }

        onNodeWithTag("settings-section-Security").performClick()
        onNodeWithTag("security-status-text").assertExists()
        onNodeWithTag("security-enable-switch").assertExists()

        // Set a PIN and enable the lock.
        onNodeWithTag("security-pin-field").performScrollTo().performTextInput("1234")
        onNodeWithTag("security-pin-confirm-field").performScrollTo().performTextInput("1234")
        onNodeWithTag("security-enable-button").performScrollTo().performClick()

        val enabled = prefStore.load()
        enabled.appLockEnabled shouldBe true
        enabled.appLockPinHash.isNotBlank() shouldBe true
        enabled.appLockPinHash shouldNotBe "1234"
        enabled.appLockPinSalt.isNotBlank() shouldBe true
        enabled.appLockPinIterations shouldBe mihon.desktop.security.PinHasher.DEFAULT_ITERATIONS

        // Configure lock behavior.
        onNodeWithTag("security-lock-on-startup-switch").performScrollTo().performClick()
        prefStore.load().appLockOnStartup shouldBe false
        onNodeWithTag("security-timeout-1").performScrollTo().performClick()
        prefStore.load().appLockIdleTimeoutMinutes shouldBe 1

        // Change the PIN.
        onNodeWithTag("security-current-pin-field").performScrollTo().performTextInput("1234")
        onNodeWithTag("security-new-pin-field").performScrollTo().performTextInput("5678")
        onNodeWithTag("security-new-pin-confirm-field").performScrollTo().performTextInput("5678")
        onNodeWithTag("security-change-pin-button").performScrollTo().performClick()
        appLockController.verifyPin("5678") shouldBe true
        appLockController.verifyPin("1234") shouldBe false

        // Manual lock from settings.
        onNodeWithTag("security-lock-now-button").performScrollTo().performClick()
        appLockController.isLocked.value shouldBe true
        appLockController.unlock("5678") shouldBe mihon.desktop.security.UnlockResult.Success

        // Disable clears the credential but leaves the rest of the preferences alone.
        onNodeWithTag("security-disable-button").performScrollTo().performClick()
        prefStore.load().appLockEnabled shouldBe false
        prefStore.load().appLockPinHash shouldBe ""
        prefStore.load().appLockPinSalt shouldBe ""
        prefStore.load().appLockPinIterations shouldBe 0
    }
}
