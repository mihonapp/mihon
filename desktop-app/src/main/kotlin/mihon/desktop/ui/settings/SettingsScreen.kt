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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.reader.DesktopReaderSettings
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
}

@Composable
fun SettingsScreen(
    preferenceStore: DesktopPreferenceStore,
    readerSettingsStore: DesktopReaderSettingsStore,
    diagnosticService: DiagnosticBundleService? = null,
    onImportBackup: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedSection by remember { mutableStateOf(SettingsSection.General) }

    Row(modifier = modifier.fillMaxSize().testTag("settings-screen")) {
        // Left side sections navigation
        Surface(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Settings",
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
                            text = section.label,
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
                SettingsSection.General -> GeneralSettingsPane(preferenceStore)
                SettingsSection.Appearance -> AppearanceSettingsPane(preferenceStore)
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
private fun GeneralSettingsPane(preferenceStore: DesktopPreferenceStore) {
    var preferences by remember { mutableStateOf(preferenceStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("General", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Application Info", fontWeight = FontWeight.Bold)
                Text("Version: 1.0.0-desktop (Phase 7)")
                Text("Platform: Windows x64")
            }
        }
    }
}

@Composable
private fun AppearanceSettingsPane(preferenceStore: DesktopPreferenceStore) {
    var preferences by remember { mutableStateOf(preferenceStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Theme Mode", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = preferences.themeMode == mode
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(mode.name)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    preferences = preferences.copy(themeMode = mode)
                                    preferenceStore.save(preferences)
                                },
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(mode.name)
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
    var settings by remember { mutableStateOf(readerSettingsStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Reader", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Default Reading Mode", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReadingMode.SINGLE_LTR to "Left to Right",
                        ReadingMode.SINGLE_RTL to "Right to Left",
                        ReadingMode.WEBTOON to "Webtoon/Vertical",
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

                Text("Default Scale Mode", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ScaleMode.FIT_WIDTH to "Fit Width",
                        ScaleMode.FIT_HEIGHT to "Fit Height",
                        ScaleMode.ORIGINAL to "Original Size",
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
                        Text("Double Page Cover Offset", fontWeight = FontWeight.Bold)
                        Text(
                            "Shift double page spread by 1 page for covers",
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

                Text("Mouse Wheel Behavior", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderWheelBehavior.entries.forEach { behavior ->
                        val isSelected = settings.wheelBehavior == behavior
                        if (isSelected) {
                            Button(onClick = {}) { Text(behavior.name.replace('_', ' ')) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(wheelBehavior = behavior)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(behavior.name.replace('_', ' '))
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
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Download Location", fontWeight = FontWeight.Bold)
                Text("Default downloads folder inside app data directory.")
                Text("Automatic resume enabled.")
            }
        }
    }
}

@Composable
private fun TrackingSettingsPane() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tracking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Supported Trackers", fontWeight = FontWeight.Bold)
                Text("MyAnimeList, AniList, Kitsu, Shikimori, Bangumi, Komga, MangaUpdates, Kavita, Suwayomi")
                Text("Offline synchronization queue: Active (atomic JSON persistence)")
            }
        }
    }
}

@Composable
private fun BackupSettingsPane(
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Backup & Restore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cross-Platform Backup Exchange", fontWeight = FontWeight.Bold)
                Text(
                    "Mihon W produces full Android-compatible ProtoBuf .tachibk backups (gzipped) containing your library, categories, reading history, tracking records, and preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.testTag("settings-export-backup-button"),
                    ) {
                        Text("Export Backup (.tachibk)")
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.testTag("settings-import-backup-button"),
                    ) {
                        Text("Import Backup (.tachibk)")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsPane(diagnosticService: DiagnosticBundleService?) {
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<DiagnosticSummary?>(null) }
    var checkingIntegrity by remember { mutableStateOf(false) }
    var bundleExportPath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Advanced & Diagnostics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Database & System Diagnostics", fontWeight = FontWeight.Bold)
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
                            Text("Run Integrity Check")
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
                        Text("Export Diagnostic Bundle (.zip)")
                    }
                }

                summary?.let { s ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Database Integrity: ${s.databaseIntegrity.joinToString(", ")}",
                        fontWeight = FontWeight.Medium,
                    )
                    Text("OS: ${s.osName} ${s.osVersion} (${s.osArch})")
                    Text("Java Runtime: ${s.javaVersion} (${s.javaVendor})")
                    Text("Log Files Count: ${s.logFileCount}")
                }

                bundleExportPath?.let { path ->
                    Text(
                        "Diagnostic bundle exported to: $path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
