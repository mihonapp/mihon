package mihon.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.feature.extension.missing.MissingExtensionsScreen
import mihon.sync.SyncPreferences
import mihon.sync.job.SyncJob
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Everything to do with keeping devices in step, in one place.
 *
 * The account and what can be done with it — syncing now, the history, the storage — come first, in
 * one card: they are actions, not settings. Below it only the choices that shape the sync remain,
 * and options that depend on a switch sit indented under it, so turning that switch off visibly
 * takes its dependants with it.
 */
object SyncSettingsScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_sync

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val auth = remember { context.appGraph.googleDriveAuth }
        val syncPreferences = remember { context.appGraph.syncPreferences }

        // Without a client ID there is nothing to sign in to, so the screen is inert and says why.
        if (!auth.isConfigured) {
            return listOf(
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.pref_sync_unavailable)),
            )
        }

        val accountEmail by syncPreferences.accountEmail().collectAsState()
        val refreshToken by syncPreferences.refreshToken().collectAsState()
        val isEnabled by syncPreferences.isEnabled().collectAsState()
        val isLinked = refreshToken.isNotBlank()
        val isSignedIn = accountEmail.isNotBlank() || isLinked
        val active = isSignedIn && isEnabled

        return listOf(
            getAccountCard(isLinked = isLinked, accountEmail = accountEmail, isEnabled = isEnabled),
            getScheduleGroup(isSignedIn = isSignedIn, active = active, syncPreferences = syncPreferences),
            getSourcesGroup(active = active, syncPreferences = syncPreferences),
            Preference.PreferenceItem.InfoPreference(
                stringResource(MR.strings.pref_sync_content_info) + " " +
                    stringResource(MR.strings.pref_sync_deletion_info),
            ),
        )
    }

    @Composable
    private fun getAccountCard(isLinked: Boolean, accountEmail: String, isEnabled: Boolean): Preference {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow
        val auth = remember { context.appGraph.googleDriveAuth }
        val syncPreferences = remember { context.appGraph.syncPreferences }
        val lastSyncAt by syncPreferences.lastSyncAt().collectAsState()

        return Preference.PreferenceItem.CustomPreference(
            title = stringResource(MR.strings.pref_sync_group_account),
        ) {
            SyncAccountCard(
                accountEmail = accountEmail,
                isLinked = isLinked,
                isEnabled = isEnabled,
                lastSyncAt = lastSyncAt,
                onLink = {
                    try {
                        uriHandler.openUri(auth.buildAuthorizationUrl().toString())
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Could not open the Google sign-in page" }
                        context.toast(MR.strings.sync_login_failed)
                    }
                },
                onUnlink = {
                    auth.logout()
                    syncPreferences.isEnabled().set(false)
                    SyncJob.setupTask(context)
                },
                onSyncNow = {
                    if (SyncJob.isRunning(context)) {
                        context.toast(MR.strings.sync_in_progress)
                    } else {
                        SyncJob.startNow(context, visible = true)
                    }
                },
                onOpenHistory = { navigator.push(SyncHistoryScreen()) },
                onOpenStorage = { navigator.push(SyncStorageScreen) },
            )
        }
    }

    @Composable
    private fun getScheduleGroup(
        isSignedIn: Boolean,
        active: Boolean,
        syncPreferences: SyncPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_sync_group_when),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.isEnabled(),
                    title = stringResource(MR.strings.pref_sync_enable),
                    visible = isSignedIn,
                    onValueChanged = {
                        // Scheduling follows the toggle, so turning sync off drops the periodic job.
                        SyncJob.setupTask(context)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnAppLifecycle(),
                    title = stringResource(MR.strings.pref_sync_on_app_lifecycle),
                    visible = active,
                    indented = true,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnAction(),
                    title = stringResource(MR.strings.pref_sync_on_action),
                    visible = active,
                    indented = true,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnLibraryUpdate(),
                    title = stringResource(MR.strings.pref_sync_on_library_update),
                    visible = active,
                    indented = true,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = syncPreferences.syncInterval(),
                    entries = mapOf(
                        0 to stringResource(MR.strings.off),
                        6 to stringResource(MR.strings.update_6hour),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                    ),
                    title = stringResource(MR.strings.pref_sync_interval),
                    visible = active,
                    indented = true,
                    onValueChanged = {
                        SyncJob.setupTask(context, it)
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getSourcesGroup(active: Boolean, syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow
        val syncStores by syncPreferences.syncExtensionStores().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_sync_group_sources),
            visible = active,
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncExtensionStores(),
                    title = stringResource(MR.strings.pref_sync_extension_stores),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncPinnedSources(),
                    title = stringResource(MR.strings.pref_sync_pinned_sources),
                    subtitle = stringResource(MR.strings.pref_sync_pinned_sources_summary),
                    visible = syncStores,
                    indented = true,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncInstalledExtensions(),
                    title = stringResource(MR.strings.pref_sync_installed_extensions),
                    subtitle = stringResource(MR.strings.pref_sync_installed_extensions_summary),
                    visible = syncStores,
                    indented = true,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.missing_ext_screen_title),
                    subtitle = stringResource(MR.strings.missing_ext_screen_summary),
                    onClick = { navigator.push(MissingExtensionsScreen()) },
                ),
            ),
        )
    }
}
