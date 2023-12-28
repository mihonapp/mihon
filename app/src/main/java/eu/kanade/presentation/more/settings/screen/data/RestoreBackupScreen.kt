package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

class RestoreBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { RestoreBackupScreenModel() }
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_restore_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            if (state.error != null) {
                val onDismissRequest = model::clearError
                when (val err = state.error) {
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
                                            append(
                                                "\n\n",
                                            ).append(stringResource(MR.strings.backup_restore_missing_sources))
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
                                        BackupRestoreJob.start(
                                            context = context,
                                            uri = err.uri,
                                            options = state.options,
                                        )
                                        onDismissRequest()
                                    },
                                ) {
                                    Text(text = stringResource(MR.strings.action_restore))
                                }
                            },
                        )
                    }
                    else -> onDismissRequest() // Unknown
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
                    BackupFileValidator(context).validate(it)
                } catch (e: Exception) {
                    model.setError(InvalidRestore(it, e.message.toString()))
                    return@rememberLauncherForActivityResult
                }

                if (results.missingSources.isEmpty() && results.missingTrackers.isEmpty()) {
                    BackupRestoreJob.start(
                        context = context,
                        uri = it,
                        options = state.options,
                    )
                    return@rememberLauncherForActivityResult
                }

                model.setError(MissingRestoreComponents(it, results.missingSources, results.missingTrackers))
            }

            LazyColumn(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(MR.strings.restore_miui_warning)
                    }
                }

                item {
                    Button(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        onClick = {
                            if (!BackupRestoreJob.isRunning(context)) {
                                // no need to catch because it's wrapped with a chooser
                                chooseBackup.launch("*/*")
                            } else {
                                context.toast(MR.strings.restore_in_progress)
                            }
                        },
                    ) {
                        Text(stringResource(MR.strings.pref_restore_backup))
                    }
                }

                // TODO: show validation errors inline
                // TODO: show options for what to restore
            }
        }
    }
}

private class RestoreBackupScreenModel : StateScreenModel<RestoreBackupScreenModel.State>(State()) {

    fun setError(error: Any) {
        mutableState.update {
            it.copy(error = error)
        }
    }

    fun clearError() {
        mutableState.update {
            it.copy(error = null)
        }
    }

    @Immutable
    data class State(
        val error: Any? = null,
        // TODO: allow user-selectable restore options
        val options: RestoreOptions = RestoreOptions(),
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
