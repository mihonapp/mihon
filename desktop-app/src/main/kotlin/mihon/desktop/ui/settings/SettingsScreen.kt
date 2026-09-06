package mihon.desktop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import java.nio.file.Path

enum class SettingsSection(val label: String) {
    General("General"),
    Appearance("Appearance"),
    Reader("Reader"),
    Downloads("Downloads"),
    Tracking("Tracking"),
    Backup("Backup & Restore"),
    Advanced("Advanced & Diagnostics"),
    ;

    fun localized(strings: DesktopStrings): String = when (this) {
        General -> strings.settingsSectionGeneral
        Appearance -> strings.settingsSectionAppearance
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
    onImportBackup: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onPreferencesChanged: ((DesktopPreferences) -> Unit)? = null,
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
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Text(
                            text = section.localized(strings),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // Right side content
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
        ) {
            when (selectedSection) {
                SettingsSection.General -> GeneralSettingsPane(preferenceStore, onPreferencesChanged)
                SettingsSection.Appearance -> AppearanceSettingsPane(preferenceStore, onPreferencesChanged)
                SettingsSection.Reader -> ReaderSettingsPane(readerSettingsStore)
                SettingsSection.Downloads -> DownloadsSettingsPane()
                SettingsSection.Tracking -> TrackingSettingsPane()
                SettingsSection.Backup -> BackupSettingsPane(onImportBackup, onExportBackup)
                SettingsSection.Advanced -> AdvancedSettingsPane(diagnosticService)
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
            }
        }
    }
}

@Composable
private fun DownloadsSettingsPane() {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionDownloads,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.settingsDownloadLocation, fontWeight = FontWeight.Bold)
                Text(strings.settingsDefaultStorageFolder)
            }
        }
    }
}

@Composable
private fun TrackingSettingsPane() {
    val strings = LocalStrings.current
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
                Text(strings.settingsConnectedTrackers(5))
            }
        }
    }
}

@Composable
private fun BackupSettingsPane(
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
) {
    val strings = LocalStrings.current
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
    }
}

@Composable
private fun AdvancedSettingsPane(diagnosticService: DiagnosticBundleService?) {
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
