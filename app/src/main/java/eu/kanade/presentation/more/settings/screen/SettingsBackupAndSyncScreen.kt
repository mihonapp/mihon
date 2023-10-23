package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.permissions.PermissionRequestHelper
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupConst
import eu.kanade.tachiyomi.data.backup.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.domain.sync.SyncPreferences
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.isScrolledToEnd
import tachiyomi.presentation.core.util.isScrolledToStart
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsBackupAndSyncScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.label_backup_and_sync

    @Composable
    override fun getPreferences(): List<Preference> {
        val backupPreferences = Injekt.get<BackupPreferences>()

        PermissionRequestHelper.requestStoragePermission()
        val syncPreferences = remember { Injekt.get<SyncPreferences>() }
        val syncService by syncPreferences.syncService().collectAsState()

        return listOf(
            getManualBackupGroup(),
            getAutomaticBackupGroup(backupPreferences = backupPreferences),
        ) + listOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.label_sync),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        pref = syncPreferences.syncService(),
                        title = stringResource(R.string.pref_sync_service),
                        entries = mapOf(
                            SyncManager.SyncService.GOOGLE_DRIVE.value to stringResource(R.string.google_drive),
                            SyncManager.SyncService.SYNCYOMI.value to stringResource(R.string.syncyomi),
                        ),
                        onValueChanged = { true },
                    ),
                ),
            ),
        ) + getSyncServicePreferences(syncPreferences, syncService)
    }

    @Composable
    private fun getManualBackupGroup(): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_backup_manual_category),
            preferenceItems = listOf(
                getCreateBackupPref(),
                getRestoreBackupPref(),
            ),
        )
    }

    @Composable
    private fun getAutomaticBackupGroup(
        backupPreferences: BackupPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val backupIntervalPref = backupPreferences.backupInterval()
        val backupInterval by backupIntervalPref.collectAsState()
        val backupDirPref = backupPreferences.backupsDirectory()
        val backupDir by backupDirPref.collectAsState()
        val pickBackupLocation = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                context.contentResolver.takePersistableUriPermission(uri, flags)

                val file = UniFile.fromUri(context, uri)
                backupDirPref.set(file.uri.toString())
            }
        }

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_backup_service_category),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = backupIntervalPref,
                    title = stringResource(R.string.pref_backup_interval),
                    entries = mapOf(
                        0 to stringResource(R.string.off),
                        6 to stringResource(R.string.update_6hour),
                        12 to stringResource(R.string.update_12hour),
                        24 to stringResource(R.string.update_24hour),
                        48 to stringResource(R.string.update_48hour),
                        168 to stringResource(R.string.update_weekly),
                    ),
                    onValueChanged = {
                        BackupCreateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_backup_directory),
                    enabled = backupInterval != 0,
                    subtitle = remember(backupDir) {
                        (UniFile.fromUri(context, backupDir.toUri())?.filePath)?.let {
                            "$it/automatic"
                        }
                    } ?: stringResource(R.string.invalid_location, backupDir),
                    onClick = {
                        try {
                            pickBackupLocation.launch(null)
                        } catch (e: ActivityNotFoundException) {
                            context.toast(R.string.file_picker_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = backupPreferences.numberOfBackups(),
                    enabled = backupInterval != 0,
                    title = stringResource(R.string.pref_backup_slots),
                    entries = listOf(2, 3, 4, 5).associateWith { it.toString() },
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(R.string.backup_info)),
            ),
        )
    }

    @Composable
    private fun getCreateBackupPref(): Preference.PreferenceItem.TextPreference {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        var flag by rememberSaveable { mutableIntStateOf(0) }
        val chooseBackupDir = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) {
            if (it != null) {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                BackupCreateJob.startNow(context, it, flag)
            }
            flag = 0
        }
        var showCreateDialog by rememberSaveable { mutableStateOf(false) }
        if (showCreateDialog) {
            CreateBackupDialog(
                onConfirm = {
                    showCreateDialog = false
                    flag = it
                    try {
                        chooseBackupDir.launch(Backup.getFilename())
                    } catch (e: ActivityNotFoundException) {
                        flag = 0
                        context.toast(R.string.file_picker_error)
                    }
                },
                onDismissRequest = { showCreateDialog = false },
            )
        }

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(R.string.pref_create_backup),
            subtitle = stringResource(R.string.pref_create_backup_summ),
            onClick = {
                scope.launch {
                    if (!BackupCreateJob.isManualJobRunning(context)) {
                        if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                            context.toast(R.string.restore_miui_warning, Toast.LENGTH_LONG)
                        }
                        showCreateDialog = true
                    } else {
                        context.toast(R.string.backup_in_progress)
                    }
                }
            },
        )
    }

    @Composable
    private fun CreateBackupDialog(
        onConfirm: (flag: Int) -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        val choices = remember {
            mapOf(
                BackupConst.BACKUP_CATEGORY to R.string.categories,
                BackupConst.BACKUP_CHAPTER to R.string.chapters,
                BackupConst.BACKUP_TRACK to R.string.track,
                BackupConst.BACKUP_HISTORY to R.string.history,
                BackupConst.BACKUP_APP_PREFS to R.string.app_settings,
                BackupConst.BACKUP_SOURCE_PREFS to R.string.source_settings,
            )
        }
        val flags = remember { choices.keys.toMutableStateList() }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(R.string.backup_choice)) },
            text = {
                Box {
                    val state = rememberLazyListState()
                    ScrollbarLazyColumn(state = state) {
                        item {
                            LabeledCheckbox(
                                label = stringResource(R.string.manga),
                                checked = true,
                                onCheckedChange = {},
                            )
                        }
                        choices.forEach { (k, v) ->
                            item {
                                val isSelected = flags.contains(k)
                                LabeledCheckbox(
                                    label = stringResource(v),
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) {
                                            flags.add(k)
                                        } else {
                                            flags.remove(k)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (!state.isScrolledToStart()) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                    if (!state.isScrolledToEnd()) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val flag = flags.fold(initial = 0, operation = { a, b -> a or b })
                        onConfirm(flag)
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
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
                        title = { Text(text = stringResource(R.string.invalid_backup_file)) },
                        text = { Text(text = listOfNotNull(err.uri, err.message).joinToString("\n\n")) },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    context.copyToClipboard(err.message, err.message)
                                    onDismissRequest()
                                },
                            ) {
                                Text(text = stringResource(R.string.action_copy_to_clipboard))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismissRequest) {
                                Text(text = stringResource(R.string.action_ok))
                            }
                        },
                    )
                }
                is MissingRestoreComponents -> {
                    AlertDialog(
                        onDismissRequest = onDismissRequest,
                        title = { Text(text = stringResource(R.string.pref_restore_backup)) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            ) {
                                val msg = buildString {
                                    append(stringResource(R.string.backup_restore_content_full))
                                    if (err.sources.isNotEmpty()) {
                                        append("\n\n").append(stringResource(R.string.backup_restore_missing_sources))
                                        err.sources.joinTo(
                                            this,
                                            separator = "\n- ",
                                            prefix = "\n- ",
                                        )
                                    }
                                    if (err.trackers.isNotEmpty()) {
                                        append("\n\n").append(stringResource(R.string.backup_restore_missing_trackers))
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
                                Text(text = stringResource(R.string.action_restore))
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
                    return Intent.createChooser(intent, context.getString(R.string.file_select_backup))
                }
            },
        ) {
            if (it == null) {
                error = InvalidRestore(message = context.getString(R.string.file_null_uri_error))
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
            title = stringResource(R.string.pref_restore_backup),
            subtitle = stringResource(R.string.pref_restore_backup_summ),
            onClick = {
                if (!BackupRestoreJob.isRunning(context)) {
                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                        context.toast(R.string.restore_miui_warning, Toast.LENGTH_LONG)
                    }
                    // no need to catch because it's wrapped with a chooser
                    chooseBackup.launch("*/*")
                } else {
                    context.toast(R.string.restore_in_progress)
                }
            },
        )
    }

    @Composable
    private fun getSyncServicePreferences(syncPreferences: SyncPreferences, syncService: Int): List<Preference> {
        val syncServiceType = SyncManager.SyncService.fromInt(syncService)
        return when (syncServiceType) {
            SyncManager.SyncService.SYNCYOMI -> getSelfHostPreferences(syncPreferences)
            SyncManager.SyncService.GOOGLE_DRIVE -> getGoogleDrivePreferences()
            else -> {
                emptyList()
            }
        } + listOf(getSyncNowPref(), getAutomaticSyncGroup(syncPreferences))
    }

    @Composable
    private fun getGoogleDrivePreferences(): List<Preference> {
        val context = LocalContext.current
        val googleDriveSync = Injekt.get<GoogleDriveService>()
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_google_drive_sign_in),
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
        val showPurgeDialog = remember { mutableStateOf(false) }
        val context = LocalContext.current
        val googleDriveSync = remember { GoogleDriveSyncService(context) }

        if (showPurgeDialog.value) {
            PurgeConfirmationDialog(
                onConfirm = {
                    showPurgeDialog.value = false
                    scope.launch {
                        val result = googleDriveSync.deleteSyncDataFromGoogleDrive()
                        when (result) {
                            GoogleDriveSyncService.DeleteSyncDataStatus.NOT_INITIALIZED -> context.toast(R.string.google_drive_not_signed_in)
                            GoogleDriveSyncService.DeleteSyncDataStatus.NO_FILES -> context.toast(R.string.google_drive_sync_data_not_found)
                            GoogleDriveSyncService.DeleteSyncDataStatus.SUCCESS -> context.toast(R.string.google_drive_sync_data_purged)
                        }
                    }
                },
                onDismissRequest = { showPurgeDialog.value = false },
            )
        }

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(R.string.pref_google_drive_purge_sync_data),
            onClick = { showPurgeDialog.value = true },
        )
    }

    @Composable
    fun PurgeConfirmationDialog(
        onConfirm: () -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(R.string.pref_purge_confirmation_title)) },
            text = { Text(text = stringResource(R.string.pref_purge_confirmation_message)) },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    @Composable
    private fun getSelfHostPreferences(syncPreferences: SyncPreferences): List<Preference> {
        return listOf(
            Preference.PreferenceItem.EditTextPreference(
                title = stringResource(R.string.pref_sync_device_name),
                subtitle = stringResource(R.string.pref_sync_device_name_summ),
                pref = syncPreferences.deviceName(),
            ),
            Preference.PreferenceItem.EditTextPreference(
                title = stringResource(R.string.pref_sync_host),
                subtitle = stringResource(R.string.pref_sync_host_summ),
                pref = syncPreferences.syncHost(),
            ),
            Preference.PreferenceItem.EditTextPreference(
                title = stringResource(R.string.pref_sync_api_key),
                subtitle = stringResource(R.string.pref_sync_api_key_summ),
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
                            context.toast(R.string.sync_in_progress)
                        }
                    }
                },
                onDismissRequest = { showDialog = false },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_sync_now_group_title),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_sync_now),
                    subtitle = stringResource(R.string.pref_sync_now_subtitle),
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
        val lastSync by syncPreferences.syncLastSync().collectAsState()
        val formattedLastSync = DateUtils.getRelativeTimeSpanString(lastSync.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_sync_service_category),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = syncIntervalPref,
                    title = stringResource(R.string.pref_sync_interval),
                    entries = mapOf(
                        0 to stringResource(R.string.off),
                        30 to stringResource(R.string.update_30min),
                        60 to stringResource(R.string.update_1hour),
                        180 to stringResource(R.string.update_3hour),
                        360 to stringResource(R.string.update_6hour),
                        720 to stringResource(R.string.update_12hour),
                        1440 to stringResource(R.string.update_24hour),
                        2880 to stringResource(R.string.update_48hour),
                        10080 to stringResource(R.string.update_weekly),
                    ),
                    onValueChanged = {
                        SyncDataJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(R.string.last_synchronization, formattedLastSync)),
            ),
        )
    }

    @Composable
    fun SyncConfirmationDialog(
        onConfirm: () -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(R.string.pref_sync_confirmation_title)) },
            text = { Text(text = stringResource(R.string.pref_sync_confirmation_message)) },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
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
