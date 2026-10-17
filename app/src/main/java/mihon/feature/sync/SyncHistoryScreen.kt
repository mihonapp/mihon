package mihon.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.util.system.toast
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DeleteSweep
import mihon.sync.model.SyncHistoryEntry
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Shows what every device did during each sync round, read back from the shared log on Drive.
 */
class SyncHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<SyncHistoryViewModel>()
        val state by viewModel.state.collectAsState()
        var confirmClear by remember { mutableStateOf(false) }

        if (confirmClear) {
            SyncConfirmDialog(
                text = stringResource(MR.strings.pref_sync_clear_history_confirm),
                onDismissRequest = { confirmClear = false },
                onConfirm = {
                    confirmClear = false
                    viewModel.clear { cleared ->
                        context.toast(if (cleared) MR.strings.pref_sync_history_cleared else MR.strings.sync_error)
                    }
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_sync_history),
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.pref_sync_clear_history),
                                    icon = MaterialSymbols.Rounded.DeleteSweep,
                                    onClick = { confirmClear = true },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.loading -> LoadingScreen(Modifier.padding(contentPadding))
                state.error -> EmptyScreen(
                    stringResource(MR.strings.pref_sync_history_unavailable),
                    modifier = Modifier.padding(contentPadding),
                )
                state.entries.isEmpty() -> EmptyScreen(
                    stringResource(MR.strings.pref_sync_history_empty),
                    modifier = Modifier.padding(contentPadding),
                )
                else -> HistoryList(
                    entries = state.entries,
                    thisDeviceId = viewModel.thisDeviceId,
                    contentPadding = contentPadding,
                )
            }
        }
    }

    @Composable
    private fun HistoryList(
        entries: List<SyncHistoryEntry>,
        thisDeviceId: String,
        contentPadding: PaddingValues,
    ) {
        LazyColumn(contentPadding = contentPadding) {
            items(entries) { entry ->
                HistoryRow(entry = entry, isThisDevice = entry.deviceId == thisDeviceId)
                HorizontalDivider()
            }
        }
    }

    @Composable
    private fun HistoryRow(entry: SyncHistoryEntry, isThisDevice: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            val deviceLabel = if (isThisDevice) {
                stringResource(MR.strings.pref_sync_history_this_device, entry.deviceName)
            } else {
                entry.deviceName
            }

            Text(
                text = deviceLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = relativeTimeSpanString(entry.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!entry.ok) {
                Text(
                    text = entry.error ?: stringResource(MR.strings.sync_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            if (!entry.hasChanges) {
                Text(
                    text = stringResource(MR.strings.pref_sync_history_no_change),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                text = stringResource(
                    MR.strings.pref_sync_history_counts,
                    entry.pulled,
                    entry.pushed,
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            DetailLine(MR.strings.pref_sync_history_added, entry.added)
            DetailLine(MR.strings.pref_sync_history_removed, entry.removed)
            DetailLine(MR.strings.pref_sync_history_progressed, entry.progressed)
        }
    }

    @Composable
    private fun DetailLine(label: StringResource, titles: List<String>) {
        if (titles.isEmpty()) return

        Text(
            text = stringResource(label, titles.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
