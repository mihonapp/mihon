package mihon.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * What the sync occupies, on the device and on the account, and the way to make this device start
 * over. Looked at once in a while, so kept off the main sync settings.
 */
object SyncStorageScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_sync_group_storage

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val syncPreferences = remember { context.appGraph.syncPreferences }
        var confirmReset by remember { mutableStateOf(false) }

        if (confirmReset) {
            SyncConfirmDialog(
                text = stringResource(MR.strings.pref_sync_reset_confirm),
                onDismissRequest = { confirmReset = false },
                onConfirm = {
                    confirmReset = false
                    syncPreferences.clearRemoteState()
                    context.toast(MR.strings.pref_sync_reset_done)
                },
            )
        }

        return listOf(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(MR.strings.pref_sync_group_storage),
                content = { SyncStorageInfo() },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_sync_reset),
                subtitle = stringResource(MR.strings.pref_sync_reset_summary),
                onClick = { confirmReset = true },
            ),
        )
    }
}
