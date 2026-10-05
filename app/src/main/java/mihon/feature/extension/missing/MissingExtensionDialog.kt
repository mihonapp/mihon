package mihon.feature.extension.missing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.tachiyomi.extension.model.InstallStep
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Offered when the user tries to read an entry whose source is not installed here.
 *
 * The wall the user hits is the moment they press play, so that is where the way out belongs —
 * rather than in a settings screen they would have to know to look for.
 */
@Composable
fun MissingExtensionDialog(
    sourceId: Long,
    sourceName: String,
    onDismissRequest: () -> Unit,
    onInstalled: () -> Unit,
) {
    val viewModel = metroViewModel<MissingExtensionsViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(sourceId) { viewModel.loadForSource(sourceId) }

    // Only an install this dialog started counts as done, never one left over from elsewhere.
    var requested by remember { mutableStateOf(false) }

    LaunchedEffect(state.installed) {
        if (requested && state.installed.isNotEmpty()) {
            onInstalled()
            onDismissRequest()
        }
    }

    val missing = state.items.firstOrNull()
    val step = missing?.let { state.installs[it.extension.pkgName] }

    AlertDialog(
        // Backing out mid-download would cancel it, so the dialog holds while something is running.
        onDismissRequest = { if (!state.isInstalling) onDismissRequest() },
        title = { Text(stringResource(MR.strings.missing_ext_title)) },
        text = {
            when {
                state.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(stringResource(MR.strings.missing_ext_searching))
                }
                state.failed -> Text(stringResource(MR.strings.missing_ext_lookup_failed))
                missing == null -> Text(stringResource(MR.strings.missing_ext_none, sourceName))
                else -> Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    Text(stringResource(MR.strings.missing_ext_found, sourceName, missing.extension.name))
                    if (step != null && step != InstallStep.Idle) {
                        Text(
                            text = step.label(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (step is InstallStep.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (missing != null) {
                TextButton(
                    onClick = {
                        requested = true
                        viewModel.install(missing)
                    },
                    enabled = !state.isInstalling,
                ) {
                    Text(
                        stringResource(
                            if (step is InstallStep.Error) MR.strings.action_retry else MR.strings.ext_install,
                        ),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !state.isInstalling) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
internal fun InstallStep.label(): String = when (this) {
    InstallStep.Idle -> ""
    InstallStep.Pending -> stringResource(MR.strings.ext_pending)
    InstallStep.Downloading -> stringResource(MR.strings.ext_downloading)
    InstallStep.Installing -> stringResource(MR.strings.ext_installing)
    InstallStep.Installed -> stringResource(MR.strings.ext_installed)
    is InstallStep.Error -> stringResource(MR.strings.missing_ext_install_failed)
}
