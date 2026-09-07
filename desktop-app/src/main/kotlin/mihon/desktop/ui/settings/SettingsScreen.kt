package mihon.desktop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.diagnostics.DiagnosticBundleService
import mihon.desktop.diagnostics.DiagnosticSummary
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.desktop.track.DesktopTracker
import mihon.desktop.track.DesktopTrackerManager
import mihon.desktop.track.TrackerAuthType
import mihon.desktop.ui.track.TrackerLoginDialog
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import java.nio.file.Path

enum class SettingsSection(val label: String) {
    General("General"),
    Appearance("Appearance"),
    Library("Library"),
    Reader("Reader"),
    Downloads("Downloads"),
    Tracking("Tracking"),
    Backup("Backup & Restore"),
    Advanced("Advanced & Diagnostics"),
    ;

    fun localized(strings: DesktopStrings): String = when (this) {
        General -> strings.settingsSectionGeneral
        Appearance -> strings.settingsSectionAppearance
        Library -> strings.libraryTitle
        Reader -> strings.settingsSectionReader
        Downloads -> strings.settingsSectionDownloads
        Tracking -> strings.settingsSectionTracking
        Backup -> strings.settingsSectionBackup
        Advanced -> strings.settingsSectionAdvanced
    }
}

@Composable
fun SettingsScreen(
    preferenceStore: DesktopPreferenceStore,
    readerSettingsStore: DesktopReaderSettingsStore,
    diagnosticService: DiagnosticBundleService? = null,
    trackerManager: DesktopTrackerManager? = null,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler? = null,
    updateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler? = null,
    onOpenCookieManager: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onPreferencesChanged: ((DesktopPreferences) -> Unit)? = null,
    downloadCacheCleaner: mihon.desktop.download.DownloadCacheCleaner? = null,
    downloadsDir: Path? = null,
    diskCacheDir: Path? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var selectedSection by remember { mutableStateOf(SettingsSection.General) }

    Row(modifier = modifier.fillMaxSize().testTag("settings-screen")) {
        // Left side sections navigation
        Surface(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                SettingsSection.entries.forEach { section ->
                    val isSelected = section == selectedSection
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedSection = section }
                            .testTag("settings-section-${section.name}"),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Text(
                            text = section.localized(strings),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // Right side section content
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
        ) {
            when (selectedSection) {
                SettingsSection.General -> GeneralSettingsPane(preferenceStore, onPreferencesChanged)
                SettingsSection.Appearance -> AppearanceSettingsPane(preferenceStore, onPreferencesChanged)
                SettingsSection.Library -> LibrarySettingsPane(preferenceStore, updateScheduler, onPreferencesChanged)
                SettingsSection.Reader -> ReaderSettingsPane(readerSettingsStore)
                SettingsSection.Downloads -> DownloadsSettingsPane(preferenceStore, onPreferencesChanged)
                SettingsSection.Tracking -> TrackingSettingsPane(trackerManager)
                SettingsSection.Backup -> BackupSettingsPane(
                    preferenceStore = preferenceStore,
                    backupScheduler = backupScheduler,
                    onImportBackup = onImportBackup,
                    onExportBackup = onExportBackup,
                    onPreferencesChanged = onPreferencesChanged,
                )
                SettingsSection.Advanced -> AdvancedSettingsPane(
                    diagnosticService = diagnosticService,
                    onOpenCookieManager = onOpenCookieManager,
                    downloadCacheCleaner = downloadCacheCleaner,
                    downloadsDir = downloadsDir,
                    diskCacheDir = diskCacheDir,
                )
            }
        }
    }
}

@Composable
private fun GeneralSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    var preferences by remember { mutableStateOf(preferenceStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionGeneral,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsLanguageTitle, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        AppLanguage.System to strings.settingsLanguageSystem,
                        AppLanguage.SimplifiedChinese to strings.settingsLanguageSimplifiedChinese,
                        AppLanguage.TraditionalChinese to strings.settingsLanguageTraditionalChinese,
                        AppLanguage.English to strings.settingsLanguageEnglish,
                    ).forEach { (lang, label) ->
                        val isSelected = preferences.language == lang
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("language-button-${lang.name}"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferences.copy(language = lang)
                                    preferences = updated
                                    preferenceStore.save(updated)
                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("language-button-${lang.name}"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // Incognito Mode Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(strings.incognitoTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.incognitoDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = preferences.incognitoMode,
                    onCheckedChange = { isChecked ->
                        val updated = preferences.copy(incognitoMode = isChecked)
                        preferences = updated
                        preferenceStore.save(updated)
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("incognito-switch"),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.settingsAppInfoTitle, fontWeight = FontWeight.Bold)
                Text(strings.settingsVersionLabel("1.0.0-desktop (Phase 8)"))
                Text(strings.settingsPlatformLabel("Windows x64"))
            }
        }
    }
}

@Composable
private fun AppearanceSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    var preferences by remember { mutableStateOf(preferenceStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionAppearance,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsThemeModeTitle, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        ThemeMode.System to strings.settingsThemeSystem,
                        ThemeMode.Light to strings.settingsThemeLight,
                        ThemeMode.Dark to strings.settingsThemeDark,
                    ).forEach { (mode, label) ->
                        val isSelected = preferences.themeMode == mode
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferences.copy(themeMode = mode)
                                    preferences = updated
                                    preferenceStore.save(updated)
                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsPane(readerSettingsStore: DesktopReaderSettingsStore) {
    val strings = LocalStrings.current
    var settings by remember { mutableStateOf(readerSettingsStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionReader,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDefaultReadingMode, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReadingMode.SINGLE_LTR to strings.readerModeSingleLtr,
                        ReadingMode.SINGLE_RTL to strings.readerModeSingleRtl,
                        ReadingMode.WEBTOON to strings.readerModeWebtoon,
                    ).forEach { (mode, label) ->
                        val isSelected = settings.mode == mode
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(mode = mode)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.settingsDefaultScaleMode, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ScaleMode.FIT_WIDTH to strings.readerScaleFitWidth,
                        ScaleMode.FIT_HEIGHT to strings.readerScaleFitHeight,
                        ScaleMode.ORIGINAL to strings.readerScaleOriginal,
                    ).forEach { (scale, label) ->
                        val isSelected = settings.scaleMode == scale
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(scaleMode = scale)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(strings.settingsDoubleSpread, fontWeight = FontWeight.Bold)
                        Text(
                            strings.settingsDoubleSpread,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.coverOffset,
                        onCheckedChange = { checked ->
                            settings = settings.copy(coverOffset = checked)
                            readerSettingsStore.save(settings)
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.settingsMouseWheelBehavior, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderWheelBehavior.PAGE_NAVIGATION to strings.settingsWheelFlipPage,
                        ReaderWheelBehavior.SCROLL to strings.settingsWheelScrollPage,
                    ).forEach { (behavior, label) ->
                        val isSelected = settings.wheelBehavior == behavior
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(wheelBehavior = behavior)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.readerColorFilter, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderColorFilter.NONE to strings.readerFilterNone,
                        ReaderColorFilter.INVERT to strings.readerFilterInvert,
                        ReaderColorFilter.GRAYSCALE to strings.readerFilterGrayscale,
                        ReaderColorFilter.SEPIA to strings.readerFilterSepia,
                        ReaderColorFilter.NIGHT to strings.readerFilterNight,
                    ).forEach { (filter, label) ->
                        val isSelected = settings.colorFilter == filter
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(colorFilter = filter)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.readerBackgroundColor, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderBackgroundColor.DARK_GRAY to strings.readerBgDarkGray,
                        ReaderBackgroundColor.BLACK to strings.readerBgBlack,
                        ReaderBackgroundColor.WHITE to strings.readerBgWhite,
                        ReaderBackgroundColor.WARM_CREAM to strings.readerBgWarmCream,
                    ).forEach { (bg, label) ->
                        val isSelected = settings.backgroundColor == bg
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(backgroundColor = bg)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerCropBorders, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.cropBorders,
                        onCheckedChange = { checked ->
                            settings = settings.copy(cropBorders = checked)
                            readerSettingsStore.save(settings)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerCropBordersWebtoon, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.cropBordersWebtoon,
                        onCheckedChange = { checked ->
                            settings = settings.copy(cropBordersWebtoon = checked)
                            readerSettingsStore.save(settings)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    var preferences by remember { mutableStateOf(preferenceStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionDownloads,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Storage path card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.settingsDownloadLocation, fontWeight = FontWeight.Bold)
                Text(
                    text = if (preferences.downloadStoragePath.isNotBlank()) {
                        preferences.downloadStoragePath
                    } else {
                        strings.settingsDefaultStorageFolder
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = preferences.downloadStoragePath,
                    onValueChange = { path ->
                        val updated = preferences.copy(downloadStoragePath = path)
                        preferences = updated
                        preferenceStore.save(updated)
                        onPreferencesChanged?.invoke(updated)
                    },
                    label = { Text(strings.settingsDownloadCustomPath) },
                    placeholder = { Text(strings.settingsDownloadCustomPathPlaceholder) },
                    modifier = Modifier.fillMaxWidth().testTag("download-storage-input"),
                    singleLine = true,
                )
            }
        }

        // Parallel downloads card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsParallelDownloads, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { count ->
                        val isSelected = preferences.downloadParallelCount == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("parallel-downloads-$count"),
                            ) {
                                Text("$count")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferences.copy(downloadParallelCount = count)
                                    preferences = updated
                                    preferenceStore.save(updated)
                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("parallel-downloads-$count"),
                            ) {
                                Text("$count")
                            }
                        }
                    }
                }
            }
        }

        // Download ahead card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDownloadAheadTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.settingsDownloadAheadDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to strings.settingsDownloadAheadDisabled,
                        1 to strings.settingsDownloadAheadChapters(1),
                        2 to strings.settingsDownloadAheadChapters(2),
                        3 to strings.settingsDownloadAheadChapters(3),
                        5 to strings.settingsDownloadAheadChapters(5),
                    ).forEach { (count, label) ->
                        val isSelected = preferences.downloadAhead == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("download-ahead-$count"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferences.copy(downloadAhead = count)
                                    preferences = updated
                                    preferenceStore.save(updated)
                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("download-ahead-$count"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // Delete read chapters card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.settingsDeleteReadChaptersTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsDeleteReadChaptersDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.deleteDownloadedRead,
                    onCheckedChange = { checked ->
                        val updated = preferences.copy(deleteDownloadedRead = checked)
                        preferences = updated
                        preferenceStore.save(updated)
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("delete-downloaded-read-switch"),
                )
            }
        }
    }
}

@Composable
private fun TrackingSettingsPane(trackerManager: DesktopTrackerManager?) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var loginTracker by remember { mutableStateOf<DesktopTracker?>(null) }
    val trackers = trackerManager?.trackers ?: emptyList()
    val loggedInCount = trackers.count { it.isLoggedIn }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionTracking,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.settingsTrackingTitle, fontWeight = FontWeight.Bold)
                Text(strings.settingsTrackingDescription)
                Text(
                    strings.settingsConnectedTrackers(loggedInCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        trackers.forEach { tracker ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("tracker-setting-card-${tracker.id}"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tracker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (tracker.isLoggedIn) {
                            Text(
                                strings.settingsTrackerLoggedInAs(tracker.username ?: "User", tracker.serverUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                strings.trackingNotLoggedIn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    if (tracker.isLoggedIn) {
                        OutlinedButton(
                            onClick = { trackerManager?.logout(tracker.id) },
                            modifier = Modifier.testTag("tracker-logout-${tracker.id}"),
                        ) {
                            Text(strings.settingsTrackerLogout)
                        }
                    } else {
                        Button(
                            onClick = { loginTracker = tracker },
                            modifier = Modifier.testTag("tracker-login-${tracker.id}"),
                        ) {
                            Text(if (tracker.authType == TrackerAuthType.SERVER) strings.trackerConnect else strings.trackerLogin)
                        }
                    }
                }
            }
        }
    }

    loginTracker?.let { tracker ->
        TrackerLoginDialog(
            tracker = tracker,
            onDismiss = { loginTracker = null },
            onLogin = { creds ->
                scope.launch {
                    trackerManager?.login(tracker.id, creds)
                    loginTracker = null
                }
            },
        )
    }
}

@Composable
private fun BackupSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler?,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf(preferenceStore.load()) }
    var backupInProgress by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionBackup,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsBackupTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.settingsBackupDescription,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.testTag("settings-export-backup-button"),
                    ) {
                        Text(strings.settingsExportBackupButton)
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.testTag("settings-import-backup-button"),
                    ) {
                        Text(strings.settingsImportBackupButton)
                    }
                }
            }
        }

        // Automated Periodic Backups Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.backupAutoTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.backupAutoDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Frequency / Interval
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupInterval, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            0 to strings.backupIntervalOff,
                            6 to strings.backupInterval6Hours,
                            12 to strings.backupInterval12Hours,
                            24 to strings.backupIntervalDaily,
                            48 to strings.backupInterval2Days,
                            168 to strings.backupIntervalWeekly,
                        ).forEach { (hours, label) ->
                            val isSelected = preferences.backupIntervalHours == hours
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("backup-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = preferences.copy(backupIntervalHours = hours)
                                        preferences = updated
                                        preferenceStore.save(updated)
                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("backup-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                // Storage Location
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupLocation, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = preferences.backupStoragePath,
                        onValueChange = { path ->
                            val updated = preferences.copy(backupStoragePath = path)
                            preferences = updated
                            preferenceStore.save(updated)
                            onPreferencesChanged?.invoke(updated)
                        },
                        label = { Text(strings.backupLocation) },
                        placeholder = { Text(strings.backupLocationDefault) },
                        modifier = Modifier.fillMaxWidth().testTag("backup-storage-input"),
                        singleLine = true,
                    )
                }

                // Retention Count
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupRetention, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 20).forEach { count ->
                            val isSelected = preferences.backupRetentionCount == count
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("backup-retention-$count"),
                                ) {
                                    Text("$count")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = preferences.copy(backupRetentionCount = count)
                                        preferences = updated
                                        preferenceStore.save(updated)
                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("backup-retention-$count"),
                                ) {
                                    Text("$count")
                                }
                            }
                        }
                    }
                }

                // Last auto backup info & Trigger Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lastBackupStr = if (preferences.lastAutoBackupEpochMillis > 0L) {
                        java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(preferences.lastAutoBackupEpochMillis),
                            java.time.ZoneId.systemDefault(),
                        ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    } else {
                        strings.backupNever
                    }
                    Text(
                        text = "${strings.backupLastAutoBackup} $lastBackupStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (backupScheduler != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    backupInProgress = true
                                    try {
                                        val path = backupScheduler.performBackup(isManual = false)
                                        preferences = preferenceStore.load()
                                        backupMessage = strings.backupExportSuccess(path.toString())
                                    } catch (e: Exception) {
                                        backupMessage = "Backup failed: ${e.message}"
                                    } finally {
                                        backupInProgress = false
                                    }
                                }
                            },
                            enabled = !backupInProgress,
                            modifier = Modifier.testTag("backup-now-button"),
                        ) {
                            Text(strings.backupNow)
                        }
                    }
                }

                backupMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySettingsPane(
    preferenceStore: DesktopPreferenceStore,
    updateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler?,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf(preferenceStore.load()) }
    var updateInProgress by remember { mutableStateOf(false) }
    var updateResultMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.libraryTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.libraryUpdateTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.libraryUpdating,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Frequency options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.libraryUpdateInterval, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            0 to strings.libraryUpdateIntervalManual,
                            6 to strings.libraryUpdateInterval6Hours,
                            12 to strings.libraryUpdateInterval12Hours,
                            24 to strings.libraryUpdateIntervalDaily,
                            48 to strings.libraryUpdateInterval2Days,
                            168 to strings.libraryUpdateIntervalWeekly,
                        ).forEach { (hours, label) ->
                            val isSelected = preferences.libraryUpdateIntervalHours == hours
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("update-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = preferences.copy(libraryUpdateIntervalHours = hours)
                                        preferences = updated
                                        preferenceStore.save(updated)
                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("update-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Filter switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipCompleted)
                    Switch(
                        checked = preferences.libraryUpdateSkipCompleted,
                        onCheckedChange = { checked ->
                            val updated = preferences.copy(libraryUpdateSkipCompleted = checked)
                            preferences = updated
                            preferenceStore.save(updated)
                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-completed-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipUnread)
                    Switch(
                        checked = preferences.libraryUpdateSkipUnread,
                        onCheckedChange = { checked ->
                            val updated = preferences.copy(libraryUpdateSkipUnread = checked)
                            preferences = updated
                            preferenceStore.save(updated)
                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-unread-switch"),
                    )
                }

                HorizontalDivider()

                // Auto download new chapters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryAutoDownloadNew)
                    Switch(
                        checked = preferences.autoDownloadNewChapters,
                        onCheckedChange = { checked ->
                            val updated = preferences.copy(autoDownloadNewChapters = checked)
                            preferences = updated
                            preferenceStore.save(updated)
                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("auto-download-new-switch"),
                    )
                }

                // Desktop notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.notificationsDesktopEnabled)
                    Switch(
                        checked = preferences.desktopNotificationsEnabled,
                        onCheckedChange = { checked ->
                            val updated = preferences.copy(desktopNotificationsEnabled = checked)
                            preferences = updated
                            preferenceStore.save(updated)
                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("desktop-notifications-switch"),
                    )
                }

                HorizontalDivider()

                // Last update & trigger button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lastUpdateStr = if (preferences.lastLibraryUpdateEpochMillis > 0) {
                        java.time.Instant.ofEpochMilli(preferences.lastLibraryUpdateEpochMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    } else {
                        strings.backupNever
                    }
                    Text(
                        text = "${strings.libraryLastUpdate} $lastUpdateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = {
                            if (updateScheduler != null && !updateInProgress) {
                                scope.launch {
                                    updateInProgress = true
                                    updateResultMessage = null
                                    try {
                                        val report = withContext(Dispatchers.IO) {
                                            updateScheduler.triggerUpdateNow()
                                        }
                                        preferences = preferenceStore.load()
                                        updateResultMessage = if (report != null) {
                                            strings.settingsLibraryUpdateResult(report.totalMangaChecked, report.newChaptersTotal)
                                        } else {
                                            strings.settingsLibraryUpdateCompleted
                                        }
                                    } catch (e: Exception) {
                                        updateResultMessage = strings.settingsLibraryUpdateFailed(e.message ?: "")
                                    } finally {
                                        updateInProgress = false
                                    }
                                }
                            }
                        },
                        enabled = !updateInProgress,
                        modifier = Modifier.testTag("update-library-now-btn"),
                    ) {
                        if (updateInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(strings.libraryUpdateNow)
                    }
                }

                updateResultMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsPane(
    diagnosticService: DiagnosticBundleService?,
    onOpenCookieManager: () -> Unit = {},
    downloadCacheCleaner: mihon.desktop.download.DownloadCacheCleaner? = null,
    downloadsDir: Path? = null,
    diskCacheDir: Path? = null,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<DiagnosticSummary?>(null) }
    var checkingIntegrity by remember { mutableStateOf(false) }
    var bundleExportPath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionAdvanced,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Storage & Cache Cleaner Card
        Card(modifier = Modifier.fillMaxWidth().testTag("storage-cleaner-card")) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.storageCleanerTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.storageCleanerDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var downloadSizeBytes by remember { mutableStateOf<Long?>(null) }
                var isCleaning by remember { mutableStateOf(false) }
                var cleanerMessage by remember { mutableStateOf<String?>(null) }

                androidx.compose.runtime.LaunchedEffect(downloadsDir) {
                    if (downloadCacheCleaner != null) {
                        downloadSizeBytes = withContext(Dispatchers.IO) {
                            downloadCacheCleaner.calculateDownloadSize()
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${strings.storageCleanerDownloadSize}: ${formatStorageSize(downloadSizeBytes ?: 0L)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("storage-cleaner-size-label"),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (downloadCacheCleaner != null) {
                                scope.launch {
                                    isCleaning = true
                                    val report = withContext(Dispatchers.IO) {
                                        downloadCacheCleaner.deleteReadChapters()
                                    }
                                    downloadSizeBytes = withContext(Dispatchers.IO) {
                                        downloadCacheCleaner.calculateDownloadSize()
                                    }
                                    isCleaning = false
                                    val freed = formatStorageSize(report.freedBytes)
                                    cleanerMessage =
                                        "${strings.storageCleanerClearReadSuccess}: ${report.deletedChaptersCount} ($freed)"
                                }
                            }
                        },
                        enabled = !isCleaning && downloadCacheCleaner != null,
                        modifier = Modifier.testTag("clean-read-chapters-button"),
                    ) {
                        Text(strings.storageCleanerClearRead)
                    }

                    OutlinedButton(
                        onClick = {
                            if (diskCacheDir != null && downloadCacheCleaner != null) {
                                scope.launch {
                                    isCleaning = true
                                    val cleared = withContext(Dispatchers.IO) {
                                        downloadCacheCleaner.clearImageDiskCache(diskCacheDir)
                                    }
                                    isCleaning = false
                                    val freed = formatStorageSize(cleared)
                                    cleanerMessage =
                                        "${strings.storageCleanerClearImageCacheSuccess}: $freed"
                                }
                            }
                        },
                        enabled = !isCleaning && diskCacheDir != null,
                        modifier = Modifier.testTag("clean-image-cache-button"),
                    ) {
                        Text(strings.storageCleanerClearImageCache)
                    }
                }

                cleanerMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("storage-cleaner-message"),
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.cookieManagerTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.cookieManagerDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenCookieManager,
                    modifier = Modifier.testTag("open-cookie-manager-button"),
                ) {
                    Text(strings.cookieManagerButton)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDiagnosticsTitle, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            diagnosticService?.let {
                                scope.launch {
                                    checkingIntegrity = true
                                    summary = withContext(Dispatchers.Default) { it.createSummary() }
                                    checkingIntegrity = false
                                }
                            }
                        },
                        modifier = Modifier.testTag("check-integrity-button"),
                    ) {
                        if (checkingIntegrity) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                        } else {
                            Text(strings.settingsRunIntegrityCheck)
                        }
                    }

                    Button(
                        onClick = {
                            diagnosticService?.let {
                                scope.launch {
                                    val tempZip = Path.of(System.getProperty("java.io.tmpdir"))
                                        .resolve("mihon-diagnostics-${System.currentTimeMillis()}.zip")
                                    withContext(Dispatchers.IO) { it.exportBundle(tempZip) }
                                    bundleExportPath = tempZip.toString()
                                }
                            }
                        },
                        modifier = Modifier.testTag("export-diagnostic-bundle-button"),
                    ) {
                        Text(strings.settingsExportDiagnosticBundle)
                    }
                }

                summary?.let { s ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        strings.settingsDatabaseIntegrity(s.databaseIntegrity.joinToString(", ")),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(strings.settingsOsInfo("${s.osName} ${s.osVersion} (${s.osArch})"))
                    Text(strings.settingsJavaInfo("${s.javaVersion} (${s.javaVendor})"))
                    Text(strings.settingsLogFilesCount(s.logFileCount))
                }

                bundleExportPath?.let { path ->
                    Text(
                        strings.settingsBundleExportedTo(path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val formatted = String.format(java.util.Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
    return "$formatted ${units[digitGroups]}"
}
