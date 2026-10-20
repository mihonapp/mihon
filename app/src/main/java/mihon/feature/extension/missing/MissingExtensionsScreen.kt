package mihon.feature.extension.missing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.InstallStep
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * The extensions the account uses that this device does not have.
 *
 * Deliberately a list of separate offers rather than one switch: a device that was left without the
 * extensions — for want of storage, usually — should be able to take back just the one series it
 * wants to read tonight.
 */
class MissingExtensionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<MissingExtensionsViewModel>()
        val state by viewModel.state.collectAsState()

        LaunchedEffect(Unit) { viewModel.refresh() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.missing_ext_screen_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.loading -> LoadingScreen(Modifier.padding(contentPadding))
                state.failed -> EmptyScreen(
                    stringResource(MR.strings.missing_ext_lookup_failed),
                    modifier = Modifier.padding(contentPadding),
                )
                state.items.isEmpty() -> EmptyScreen(
                    stringResource(MR.strings.missing_ext_empty),
                    modifier = Modifier.padding(contentPadding),
                )
                else -> MissingList(
                    state = state,
                    contentPadding = contentPadding,
                    onInstall = viewModel::install,
                    onInstallAll = viewModel::installAll,
                    onCancelAll = viewModel::cancelAll,
                )
            }
        }
    }

    @Composable
    private fun MissingList(
        state: MissingExtensionsViewModel.State,
        contentPadding: PaddingValues,
        onInstall: (MissingExtension) -> Unit,
        onInstallAll: () -> Unit,
        onCancelAll: () -> Unit,
    ) {
        LazyColumn(contentPadding = contentPadding) {
            item(key = "install-all") {
                InstallAllHeader(
                    batch = state.batch,
                    enabled = !state.isInstalling,
                    onInstallAll = onInstallAll,
                    onCancelAll = onCancelAll,
                )
            }

            items(state.items, key = { it.extension.pkgName }) { missing ->
                MissingRow(
                    missing = missing,
                    step = state.installs[missing.extension.pkgName],
                    enabled = !state.isInstalling,
                    onInstall = { onInstall(missing) },
                )
                HorizontalDivider()
            }
        }
    }

    /**
     * Either the offer to install everything, or how far that batch has got.
     *
     * Installs run one at a time, so without a count the screen would look idle for minutes while
     * it worked through the queue.
     */
    @Composable
    private fun InstallAllHeader(
        batch: MissingExtensionsViewModel.Batch?,
        enabled: Boolean,
        onInstallAll: () -> Unit,
        onCancelAll: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            if (batch == null) {
                Button(
                    onClick = onInstallAll,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(MR.strings.missing_ext_install_all))
                }
                return@Column
            }

            LinearProgressIndicator(
                progress = { (batch.done.toFloat() / batch.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        MR.strings.missing_ext_install_progress,
                        minOf(batch.done + 1, batch.total),
                        batch.total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancelAll) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
        }
    }

    @Composable
    private fun MissingRow(
        missing: MissingExtension,
        step: InstallStep?,
        enabled: Boolean,
        onInstall: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.small,
                    bottom = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Text(
                    text = missing.extension.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (missing.isNeeded) {
                        pluralStringResource(
                            MR.plurals.missing_ext_entries,
                            count = missing.entryCount,
                            missing.entryCount,
                        )
                    } else {
                        stringResource(MR.strings.missing_ext_other_device)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (missing.sourceNames.isNotEmpty()) {
                    Text(
                        text = missing.sourceNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (step is InstallStep.Error) {
                    Text(
                        text = stringResource(MR.strings.missing_ext_install_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            TextButton(onClick = onInstall, enabled = enabled) {
                Text(
                    text = when {
                        step == null || step == InstallStep.Idle -> stringResource(MR.strings.ext_install)
                        step is InstallStep.Error -> stringResource(MR.strings.action_retry)
                        else -> step.label()
                    },
                )
            }
        }
    }
}
