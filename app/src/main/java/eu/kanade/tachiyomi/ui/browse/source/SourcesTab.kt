package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.feature.mekuri.vault.MekuriVault
import mihon.feature.mekuri.vault.VaultRow
import mihon.feature.mekuri.vault.unlockMekuriVault
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.TravelExplore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

@Composable
fun Screen.sourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<SourcesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Mekuri: vault
    val vaultUnlocked by MekuriVault.unlocked.collectAsStateWithLifecycle()
    val vaultScope = rememberCoroutineScope()
    val vaultContext = LocalContext.current

    return TabContent(
        titleRes = MR.strings.label_sources,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = MaterialSymbols.Rounded.TravelExplore,
                onClick = { navigator.push(GlobalSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = MaterialSymbols.Rounded.FilterList,
                onClick = { navigator.push(SourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    navigator.push(BrowseSourceScreen(source.id, listing.query))
                },
                onClickPin = viewModel::togglePin,
                onLongClickItem = viewModel::showSourceDialog,
                // Mekuri: open / lock the vault from the end of the list
                footer = when {
                    state.vaultLockedCount > 0 -> {
                        {
                            val count = state.vaultLockedCount
                            VaultRow(
                                text = "Vault · $count hidden source" + if (count == 1) "" else "s",
                                unlocked = false,
                                onClick = { vaultScope.launch { vaultContext.unlockMekuriVault() } },
                            )
                        }
                    }
                    vaultUnlocked &&
                        state.items.any { (it as? SourceUiModel.Header)?.language == SourcesViewModel.VAULT_KEY } -> {
                        { VaultRow(text = "Vault is open", unlocked = true, onClick = MekuriVault::lock) }
                    }
                    else -> null
                },
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                SourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        viewModel.togglePin(source)
                        viewModel.closeDialog()
                    },
                    onClickDisable = {
                        viewModel.toggleSource(source)
                        viewModel.closeDialog()
                    },
                    onDismiss = viewModel::closeDialog,
                    extraOptions = {
                        if (!source.isLocal()) {
                            val inVault = MekuriVault.isSourceInVault(source.id)
                            Text(
                                text = if (inVault) "Take out of vault" else "Move to vault",
                                modifier = Modifier
                                    .clickable {
                                        MekuriVault.setSourceInVault(source.id, !inVault)
                                        viewModel.closeDialog()
                                    }
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            )
                        }
                    },
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        SourcesViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
