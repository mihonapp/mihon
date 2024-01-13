package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.domain.sync.SyncOptions
import tachiyomi.domain.sync.SyncPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SyncSettingsSelector : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { SyncSettingsSelectorModel() }
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_choose_what_to_sync),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.label_sync),
                actionEnabled = state.options.anyEnabled(),
                onClickAction = {
                    if (!SyncDataJob.isAnyJobRunning(context)) {
                        model.syncNow(context)
                        navigator.pop()
                    } else {
                        context.toast(MR.strings.sync_in_progress)
                    }
                },
            ) {
                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, model)
                    }
                }

                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, model)
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: SyncSettingsSelectorModel.State,
        model: SyncSettingsSelectorModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }
}

private class SyncSettingsSelectorModel(
    val syncPreferences: SyncPreferences = Injekt.get(),
) : StateScreenModel<SyncSettingsSelectorModel.State>(
    State(syncOptionsToBackupOptions(syncPreferences.getSyncOptions())),
) {
    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            val updatedOptions = setter(it.options, enabled)
            syncPreferences.setSyncOptions(backupOptionsToSyncOptions(updatedOptions))
            it.copy(options = updatedOptions)
        }
    }

    fun syncNow(context: Context) {
        SyncDataJob.startNow(context)
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    ) companion object {
        private fun syncOptionsToBackupOptions(syncOptions: SyncOptions): BackupOptions {
            return BackupOptions(
                libraryEntries = syncOptions.libraryEntries,
                categories = syncOptions.categories,
                chapters = syncOptions.chapters,
                tracking = syncOptions.tracking,
                history = syncOptions.history,
                appSettings = syncOptions.appSettings,
                sourceSettings = syncOptions.sourceSettings,
                privateSettings = syncOptions.privateSettings,
            )
        }

        private fun backupOptionsToSyncOptions(backupOptions: BackupOptions): SyncOptions {
            return SyncOptions(
                libraryEntries = backupOptions.libraryEntries,
                categories = backupOptions.categories,
                chapters = backupOptions.chapters,
                tracking = backupOptions.tracking,
                history = backupOptions.history,
                appSettings = backupOptions.appSettings,
                sourceSettings = backupOptions.sourceSettings,
                privateSettings = backupOptions.privateSettings,
            )
        }
    }
}
