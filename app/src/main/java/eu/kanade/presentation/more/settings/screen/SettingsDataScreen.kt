package eu.kanade.presentation.more.settings.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.permissions.PermissionRequestHelper
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.backup.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.sync.SyncPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDataScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_data_storage

    @Composable
    override fun getPreferences(): List<Preference> {
        val backupPreferences = Injekt.get<BackupPreferences>()

        PermissionRequestHelper.requestStoragePermission()

        val syncPreferences = remember { Injekt.get<SyncPreferences>() }
        val syncService by syncPreferences.syncService().collectAsState()

        return listOf(
            getBackupAndRestoreGroup(backupPreferences = backupPreferences),
            getDataGroup(),
        ) + getSyncPreferences(syncPreferences = syncPreferences, syncService = syncService)
    }

    @Composable
    private fun getBackupAndRestoreGroup(backupPreferences: BackupPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val lastAutoBackup by backupPreferences.lastAutoBackupTimestamp().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_backup),
            preferenceItems = listOf(
                // Manual actions
                getCreateBackupPref(),
                getRestoreBackupPref(),

                // Automatic backups
                Preference.PreferenceItem.ListPreference(
                    pref = backupPreferences.backupInterval(),
                    title = stringResource(MR.strings.pref_backup_interval),
                    entries = mapOf(
                        0 to stringResource(MR.strings.off),
                        6 to stringResource(MR.strings.update_6hour),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    onValueChanged = {
                        BackupCreateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.backup_info) + "\n\n" +
                        stringResource(MR.strings.last_auto_backup_info, relativeTimeSpanString(lastAutoBackup)),
                ),
            ),
        )
    }

    @Composable
    private fun getCreateBackupPref(): Preference.PreferenceItem.TextPreference {
        val navigator = LocalNavigator.currentOrThrow
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.pref_create_backup),
            subtitle = stringResource(MR.strings.pref_create_backup_summ),
            onClick = { navigator.push(CreateBackupScreen()) },
        )
    }

    @Composable
    private fun getRestoreBackupPref(): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        var error by remember { mutableStateOf<Any?>(null) }
        if (error != null) {
            val onDismissRequest = { error = null }
            when (val err = error) {
                is InvalidRestore -> {
                    AlertDialog(
                        onDismissRequest = onDismissRequest,
                        title = { Text(text = stringResource(MR.strings.invalid_backup_file)) },
                        text = { Text(text = listOfNotNull(err.uri, err.message).joinToString("\n\n")) },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    context.copyToClipboard(err.message, err.message)
                                    onDismissRequest()
                                },
                            ) {
                                Text(text = stringResource(MR.strings.action_copy_to_clipboard))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismissRequest) {
                                Text(text = stringResource(MR.strings.action_ok))
                            }
                        },
                    )
                }
                is MissingRestoreComponents -> {
                    AlertDialog(
                        onDismissRequest = onDismissRequest,
                        title = { Text(text = stringResource(MR.strings.pref_restore_backup)) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            ) {
                                val msg = buildString {
                                    append(stringResource(MR.strings.backup_restore_content_full))
                                    if (err.sources.isNotEmpty()) {
                                        append("\n\n").append(stringResource(MR.strings.backup_restore_missing_sources))
                                        err.sources.joinTo(
                                            this,
                                            separator = "\n- ",
                                            prefix = "\n- ",
                                        )
                                    }
                                    if (err.trackers.isNotEmpty()) {
                                        append(
                                            "\n\n",
                                        ).append(stringResource(MR.strings.backup_restore_missing_trackers))
                                        err.trackers.joinTo(
                                            this,
                                            separator = "\n- ",
                                            prefix = "\n- ",
                                        )
                                    }
                                }
                                Text(text = msg)
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    BackupRestoreJob.start(context, err.uri)
                                    onDismissRequest()
                                },
                            ) {
                                Text(text = stringResource(MR.strings.action_restore))
                            }
                        },
                    )
                }
                else -> error = null // Unknown
            }
        }

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    return Intent.createChooser(intent, context.stringResource(MR.strings.file_select_backup))
                }
            },
        ) {
            if (it == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }

            val results = try {
                BackupFileValidator().validate(context, it)
            } catch (e: Exception) {
                error = InvalidRestore(it, e.message.toString())
                return@rememberLauncherForActivityResult
            }

            if (results.missingSources.isEmpty() && results.missingTrackers.isEmpty()) {
                BackupRestoreJob.start(context, it)
                return@rememberLauncherForActivityResult
            }

            error = MissingRestoreComponents(it, results.missingSources, results.missingTrackers)
        }

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.pref_restore_backup),
            subtitle = stringResource(MR.strings.pref_restore_backup_summ),
            onClick = {
                if (!BackupRestoreJob.isRunning(context)) {
                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                        context.toast(MR.strings.restore_miui_warning, Toast.LENGTH_LONG)
                    }
                    // no need to catch because it's wrapped with a chooser
                    chooseBackup.launch("*/*")
                } else {
                    context.toast(MR.strings.restore_in_progress)
                }
            },
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        val chapterCache = remember { Injekt.get<ChapterCache>() }
        var cacheReadableSizeSema by remember { mutableIntStateOf(0) }
        val cacheReadableSize = remember(cacheReadableSizeSema) { chapterCache.readableSize }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_data),
            preferenceItems = listOf(
                getStorageInfoPref(cacheReadableSize),

                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_chapter_cache),
                    subtitle = stringResource(MR.strings.used_cache, cacheReadableSize),
                    onClick = {
                        scope.launchNonCancellable {
                            try {
                                val deletedFiles = chapterCache.clear()
                                withUIContext {
                                    context.toast(context.stringResource(MR.strings.cache_deleted, deletedFiles))
                                    cacheReadableSizeSema++
                                }
                            } catch (e: Throwable) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext { context.toast(MR.strings.cache_delete_error) }
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.autoClearChapterCache(),
                    title = stringResource(MR.strings.pref_auto_clear_chapter_cache),
                ),
            ),
        )
    }

    @Composable
    fun getStorageInfoPref(
        chapterCacheReadableSize: String,
    ): Preference.PreferenceItem.CustomPreference {
        val context = LocalContext.current
        val available = remember {
            Formatter.formatFileSize(context, DiskUtil.getAvailableStorageSpace(Environment.getDataDirectory()))
        }
        val total = remember {
            Formatter.formatFileSize(context, DiskUtil.getTotalStorageSpace(Environment.getDataDirectory()))
        }

        return Preference.PreferenceItem.CustomPreference(
            title = stringResource(MR.strings.pref_storage_usage),
        ) {
            BasePreferenceWidget(
                title = stringResource(MR.strings.pref_storage_usage),
                subcomponent = {
                    // TODO: downloads, SD cards, bar representation?, i18n
                    Box(modifier = Modifier.padding(horizontal = PrefsHorizontalPadding)) {
                        Text(text = "Available: $available / $total (chapter cache: $chapterCacheReadableSize)")
                    }
                },
            )
        }
    }

    @Composable
    private fun getSyncPreferences(syncPreferences: SyncPreferences, syncService: Int): List<Preference> {
        return listOf(
            Preference.PreferenceItem.ListPreference(
                pref = syncPreferences.syncService(),
                title = stringResource(MR.strings.pref_sync_service),
                entries = mapOf(
                    SyncManager.SyncService.NONE.value to stringResource(MR.strings.off),
                    SyncManager.SyncService.SYNCYOMI.value to stringResource(MR.strings.syncyomi),
                    SyncManager.SyncService.GOOGLE_DRIVE.value to stringResource(MR.strings.google_drive),
                ),
                onValueChanged = { true },
            ),
        ) + getSyncServicePreferences(syncPreferences, syncService)
    }
}

@Composable
private fun getSyncServicePreferences(syncPreferences: SyncPreferences, syncService: Int): List<Preference> {
    val syncServiceType = SyncManager.SyncService.fromInt(syncService)

    val basePreferences = getBasePreferences(syncServiceType, syncPreferences)

    return if (syncServiceType != SyncManager.SyncService.NONE) {
        basePreferences + getAdditionalPreferences(syncPreferences)
    } else {
        basePreferences
    }
}

@Composable
private fun getBasePreferences(
    syncServiceType: SyncManager.SyncService,
    syncPreferences: SyncPreferences,
): List<Preference> {
    return when (syncServiceType) {
        SyncManager.SyncService.NONE -> emptyList()
        SyncManager.SyncService.SYNCYOMI -> getSelfHostPreferences(syncPreferences)
        SyncManager.SyncService.GOOGLE_DRIVE -> getGoogleDrivePreferences()
    }
}

@Composable
private fun getAdditionalPreferences(syncPreferences: SyncPreferences): List<Preference> {
    return listOf(getSyncNowPref(), getAutomaticSyncGroup(syncPreferences))
}

@Composable
private fun getGoogleDrivePreferences(): List<Preference> {
    val context = LocalContext.current
    val googleDriveSync = Injekt.get<GoogleDriveService>()
    return listOf(
        Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.pref_google_drive_sign_in),
            onClick = {
                val intent = googleDriveSync.getSignInIntent()
                context.startActivity(intent)
            },
        ),
        getGoogleDrivePurge(),
    )
}

@Composable
private fun getGoogleDrivePurge(): Preference.PreferenceItem.TextPreference {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val googleDriveSync = remember { GoogleDriveSyncService(context) }
    var showPurgeDialog by remember { mutableStateOf(false) }

    if (showPurgeDialog) {
        PurgeConfirmationDialog(
            onConfirm = {
                showPurgeDialog = false
                scope.launch {
                    val result = googleDriveSync.deleteSyncDataFromGoogleDrive()
                    when (result) {
                        GoogleDriveSyncService.DeleteSyncDataStatus.NOT_INITIALIZED -> context.toast(
                            MR.strings.google_drive_not_signed_in,
                        )
                        GoogleDriveSyncService.DeleteSyncDataStatus.NO_FILES -> context.toast(
                            MR.strings.google_drive_sync_data_not_found,
                        )
                        GoogleDriveSyncService.DeleteSyncDataStatus.SUCCESS -> context.toast(
                            MR.strings.google_drive_sync_data_purged,
                        )
                    }
                }
            },
            onDismissRequest = { showPurgeDialog = false },
        )
    }

    return Preference.PreferenceItem.TextPreference(
        title = stringResource(MR.strings.pref_google_drive_purge_sync_data),
        onClick = { showPurgeDialog = true },
    )
}

@Composable
private fun PurgeConfirmationDialog(
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.pref_purge_confirmation_title)) },
        text = { Text(text = stringResource(MR.strings.pref_purge_confirmation_message)) },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
private fun getSelfHostPreferences(syncPreferences: SyncPreferences): List<Preference> {
    val scope = rememberCoroutineScope()
    return listOf(
        Preference.PreferenceItem.EditTextPreference(
            title = stringResource(MR.strings.pref_sync_host),
            subtitle = stringResource(MR.strings.pref_sync_host_summ),
            pref = syncPreferences.syncHost(),
            onValueChanged = { newValue ->
                scope.launch {
                    // Trim spaces at the beginning and end, then remove trailing slash if present
                    val trimmedValue = newValue.trim()
                    val modifiedValue = trimmedValue.trimEnd { it == '/' }
                    syncPreferences.syncHost().set(modifiedValue)
                }
                true
            },
        ),
        Preference.PreferenceItem.EditTextPreference(
            title = stringResource(MR.strings.pref_sync_api_key),
            subtitle = stringResource(MR.strings.pref_sync_api_key_summ),
            pref = syncPreferences.syncAPIKey(),
        ),
    )
}

@Composable
private fun getSyncNowPref(): Preference.PreferenceGroup {
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (showDialog) {
        SyncConfirmationDialog(
            onConfirm = {
                showDialog = false
                scope.launch {
                    if (!SyncDataJob.isAnyJobRunning(context)) {
                        SyncDataJob.startNow(context)
                    } else {
                        context.toast(MR.strings.sync_in_progress)
                    }
                }
            },
            onDismissRequest = { showDialog = false },
        )
    }
    return Preference.PreferenceGroup(
        title = stringResource(MR.strings.pref_sync_now_group_title),
        preferenceItems = listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_sync_now),
                subtitle = stringResource(MR.strings.pref_sync_now_subtitle),
                onClick = {
                    showDialog = true
                },
            ),
        ),
    )
}

@Composable
private fun getAutomaticSyncGroup(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
    val context = LocalContext.current
    val syncIntervalPref = syncPreferences.syncInterval()
    val lastSync by syncPreferences.lastSyncTimestamp().collectAsState()

    return Preference.PreferenceGroup(
        title = stringResource(MR.strings.pref_sync_service_category),
        preferenceItems = listOf(
            Preference.PreferenceItem.ListPreference(
                pref = syncIntervalPref,
                title = stringResource(MR.strings.pref_sync_interval),
                entries = mapOf(
                    0 to stringResource(MR.strings.off),
                    30 to stringResource(MR.strings.update_30min),
                    60 to stringResource(MR.strings.update_1hour),
                    180 to stringResource(MR.strings.update_3hour),
                    360 to stringResource(MR.strings.update_6hour),
                    720 to stringResource(MR.strings.update_12hour),
                    1440 to stringResource(MR.strings.update_24hour),
                    2880 to stringResource(MR.strings.update_48hour),
                    10080 to stringResource(MR.strings.update_weekly),
                ),
                onValueChanged = {
                    SyncDataJob.setupTask(context, it)
                    true
                },
            ),
            Preference.PreferenceItem.InfoPreference(
                stringResource(MR.strings.last_synchronization, relativeTimeSpanString(lastSync)),
            ),
        ),
    )
}

@Composable
private fun SyncConfirmationDialog(
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.pref_sync_confirmation_title)) },
        text = { Text(text = stringResource(MR.strings.pref_sync_confirmation_message)) },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

private data class MissingRestoreComponents(
    val uri: Uri,
    val sources: List<String>,
    val trackers: List<String>,
)

private data class InvalidRestore(
    val uri: Uri? = null,
    val message: String,
)
