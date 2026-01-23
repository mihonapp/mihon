package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

class MassInstallScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ExtensionsScreenModel() }
        val state by screenModel.state.collectAsState()

        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = stringResource(MR.strings.action_webview_back),
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(MR.strings.label_extensions),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Button(onClick = {
                    state.missingLibraryExtensions.forEach { ext ->
                        when (ext) {
                            is Extension.Available -> screenModel.installExtension(ext)
                            is Extension.Untrusted -> screenModel.trustExtension(ext)
                            else -> {}
                        }
                    }
                }) {
                    Text(text = stringResource(MR.strings.install_all))
                }
            }

            var trustDialogExt by remember { androidx.compose.runtime.mutableStateOf<Extension.Untrusted?>(null) }

            if (state.missingLibraryExtensions.isEmpty()) {
                EmptyScreen(MR.strings.no_results_found, modifier = Modifier.padding(MaterialTheme.padding.medium))
            } else {
                LazyColumn(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium)) {
                    items(state.missingLibraryExtensions, key = {
                        (it as? Extension.Available)?.pkgName
                            ?: (it as? Extension.Untrusted)?.pkgName
                            ?: it.hashCode().toString()
                    }) { ext ->
                        val installStep by screenModel.installStepFlow(
                            ext.pkgName,
                        ).collectAsState(initial = InstallStep.Idle)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.padding.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                ExtensionIcon(extension = ext, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = ext.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                )
                            }

                            when (ext) {
                                is Extension.Available -> {
                                    if (installStep.isCompleted()) {
                                        IconButton(onClick = { screenModel.installExtension(ext) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.GetApp,
                                                contentDescription = stringResource(MR.strings.ext_install),
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = { /* noop while installing */ }) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                }
                                is Extension.Untrusted -> {
                                    IconButton(onClick = { trustDialogExt = ext }) {
                                        Icon(
                                            imageVector = Icons.Outlined.VerifiedUser,
                                            contentDescription = stringResource(MR.strings.ext_trust),
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            val toTrust = trustDialogExt
            if (toTrust != null) {
                AlertDialog(
                    onDismissRequest = { trustDialogExt = null },
                    title = { Text(text = stringResource(MR.strings.untrusted_extension)) },
                    text = { Text(text = stringResource(MR.strings.untrusted_extension_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            screenModel.trustExtension(toTrust)
                            trustDialogExt = null
                        }) { Text(text = stringResource(MR.strings.ext_trust)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            screenModel.uninstallExtension(toTrust)
                            trustDialogExt = null
                        }) { Text(text = stringResource(MR.strings.ext_uninstall)) }
                    },
                )
            }
        }
    }
}
