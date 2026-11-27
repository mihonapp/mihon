package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.extension.model.InstallStep
import mihon.core.navigation.LocalNavigator
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Sync
import mihon.icons.materialsymbols.roundedfilled.Visibility
import mihon.icons.materialsymbols.roundedfilled.VisibilityOff
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun WebDavSyncScreen() {
    val navigator = LocalNavigator.current
    val viewModel = metroViewModel<WebDavSyncViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val preferences = viewModel.preferences
    val syncEnabled by preferences.syncEnabled.collectAsState()
    val lastSyncTimestamp by preferences.lastSyncTimestamp.collectAsState()
    val lastSyncStatus by preferences.lastSyncStatus.collectAsState()
    val syncLibrary by preferences.syncLibrary.collectAsState()
    val syncExtensionStores by preferences.syncExtensionStores.collectAsState()
    val syncExtensions by preferences.syncExtensions.collectAsState()
    val syncIntervalMinutes by preferences.syncIntervalMinutes.collectAsState()
    val syncOnReaderExit by preferences.syncOnReaderExit.collectAsState()
    val syncOnAppStart by preferences.syncOnAppStart.collectAsState()
    val syncWifiOnly by preferences.syncWifiOnly.collectAsState()

    var serverUrl by remember { mutableStateOf(preferences.serverUrl.get()) }
    var username by remember { mutableStateOf(preferences.username.get()) }
    var password by remember { mutableStateOf(preferences.password.get()) }
    var remotePath by remember { mutableStateOf(preferences.remotePath.get()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    val latestServerUrl by rememberUpdatedState(serverUrl)
    val latestUsername by rememberUpdatedState(username)
    val latestPassword by rememberUpdatedState(password)
    val latestRemotePath by rememberUpdatedState(remotePath)

    DisposableEffect(Unit) {
        onDispose {
            viewModel.updateServerConfig(
                serverUrl = latestServerUrl,
                username = latestUsername,
                password = latestPassword,
                remotePath = latestRemotePath,
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WebDavSyncViewModel.Event.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.webdav_sync_title),
                navigateUp = navigator::pop,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + MaterialTheme.padding.small,
                bottom = contentPadding.calculateBottomPadding() + MaterialTheme.padding.large,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            item(key = "status") {
                SectionCard {
                    ToggleRow(
                        title = stringResource(MR.strings.webdav_sync_enable),
                        checked = syncEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateServerConfig(serverUrl, username, password, remotePath)
                            preferences.syncEnabled.set(enabled)
                            viewModel.onScheduleSettingChanged()
                        },
                    )

                    val lastSyncedLabel = if (lastSyncTimestamp > 0L) {
                        relativeTimeSpanString(lastSyncTimestamp)
                    } else {
                        stringResource(MR.strings.webdav_sync_never)
                    }
                    Text(
                        text = stringResource(MR.strings.webdav_sync_last_synced, lastSyncedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (lastSyncStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))
                        Text(
                            text = stringResource(MR.strings.webdav_sync_last_error, lastSyncStatus),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isTestingConnection && !state.isSyncing,
                            onClick = {
                                viewModel.testConnection(serverUrl, username, password, remotePath)
                            },
                        ) {
                            if (state.isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                            }
                            Text(
                                text = stringResource(
                                    if (state.isTestingConnection) {
                                        MR.strings.webdav_sync_testing_connection
                                    } else {
                                        MR.strings.webdav_sync_test_connection
                                    },
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSyncing && !state.isTestingConnection,
                            onClick = {
                                viewModel.syncNow(serverUrl, username, password, remotePath)
                            },
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                            Text(
                                text = stringResource(MR.strings.webdav_sync_now),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (state.missingExtensions.isNotEmpty()) {
                item(key = "missing_extensions") {
                    SectionCard(MR.strings.webdav_sync_missing_extensions_section) {
                        Text(
                            text = stringResource(MR.strings.webdav_sync_missing_extensions_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = viewModel::openMissingExtensionsDialog,
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                            Text(
                                text = stringResource(
                                    MR.strings.webdav_sync_missing_extensions_title,
                                    state.missingExtensions.size,
                                ),
                            )
                        }
                    }
                }
            }

            item(key = "server") {
                SectionCard(MR.strings.webdav_sync_server_section) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(MR.strings.webdav_sync_server_url)) },
                        placeholder = { Text(stringResource(MR.strings.webdav_sync_server_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(MR.strings.webdav_sync_username)) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(MR.strings.webdav_sync_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        MaterialSymbols.RoundedFilled.VisibilityOff
                                    } else {
                                        MaterialSymbols.RoundedFilled.Visibility
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = remotePath,
                        onValueChange = { remotePath = it },
                        label = { Text(stringResource(MR.strings.webdav_sync_remote_path)) },
                        singleLine = true,
                    )
                }
            }

            item(key = "content") {
                SectionCard(MR.strings.webdav_sync_content_section) {
                    ToggleRow(
                        title = stringResource(MR.strings.webdav_sync_library),
                        subtitle = stringResource(MR.strings.webdav_sync_library_summary),
                        checked = syncLibrary,
                        onCheckedChange = preferences.syncLibrary::set,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
                    ToggleRow(
                        title = stringResource(MR.strings.webdav_sync_extension_stores),
                        subtitle = stringResource(MR.strings.webdav_sync_extension_stores_summary),
                        checked = syncExtensionStores,
                        onCheckedChange = preferences.syncExtensionStores::set,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
                    ToggleRow(
                        title = stringResource(MR.strings.webdav_sync_extensions),
                        subtitle = stringResource(MR.strings.webdav_sync_extensions_summary),
                        checked = syncExtensions,
                        onCheckedChange = preferences.syncExtensions::set,
                    )
                }
            }

            item(key = "schedule") {
                SectionCard(MR.strings.webdav_sync_schedule_section) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showIntervalDialog = true }
                            .padding(vertical = MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(MR.strings.webdav_sync_interval),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = intervalLabel(syncIntervalMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
                    ToggleRow(
                        title = stringResource(MR.strings.webdav_sync_on_reader_exit),
                        subtitle = stringResource(MR.strings.webdav_sync_on_reader_exit_summary),
                        checked = syncOnReaderExit,
                        onCheckedChange = preferences.syncOnReaderExit::set,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
                    ToggleRow(
                        title = stringResource(MR.strings.webdav_sync_on_app_start),
                        checked = syncOnAppStart,
                        onCheckedChange = preferences.syncOnAppStart::set,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
                    ToggleRow(
                        title = stringResource(MR.strings.webdav_sync_wifi_only),
                        checked = syncWifiOnly,
                        onCheckedChange = {
                            preferences.syncWifiOnly.set(it)
                            viewModel.onScheduleSettingChanged()
                        },
                    )
                }
            }
        }
    }

    if (showIntervalDialog) {
        SyncIntervalDialog(
            selectedMinutes = syncIntervalMinutes,
            onSelect = { minutes ->
                preferences.syncIntervalMinutes.set(minutes)
                viewModel.onScheduleSettingChanged()
                showIntervalDialog = false
            },
            onDismissRequest = { showIntervalDialog = false },
        )
    }

    if (state.showMissingExtensionsDialog) {
        MissingExtensionsDialog(
            items = state.missingExtensions,
            isRefreshingStores = state.isRefreshingStores,
            onInstall = viewModel::installExtension,
            onInstallAll = viewModel::installAllAvailable,
            onRefreshStores = viewModel::refreshExtensionStores,
            onDismissRequest = viewModel::dismissMissingExtensionsDialog,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SyncIntervalDialog(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val options = listOf(0, 15, 30, 60, 180, 360, 720, 1440)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.webdav_sync_interval)) },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = MaterialTheme.padding.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedMinutes == minutes,
                            onClick = { onSelect(minutes) },
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                        Text(text = intervalLabel(minutes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun MissingExtensionsDialog(
    items: List<WebDavSyncViewModel.MissingExtensionItem>,
    isRefreshingStores: Boolean,
    onInstall: (WebDavSyncViewModel.MissingExtensionItem) -> Unit,
    onInstallAll: () -> Unit,
    onRefreshStores: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val hasInstallable = items.any {
        it.available != null && (it.installStep == InstallStep.Idle || it.installStep == InstallStep.Error)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(MR.strings.webdav_sync_missing_extensions_section),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = !isRefreshingStores,
                    onClick = onRefreshStores,
                ) {
                    if (isRefreshingStores) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Refresh,
                            contentDescription = stringResource(MR.strings.webdav_sync_refresh_stores),
                        )
                    }
                }
            }
        },
        text = {
            if (items.isEmpty()) {
                Text(text = stringResource(MR.strings.webdav_sync_missing_extensions_empty))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(items = items, key = { it.synced.pkgName }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.synced.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (item.available != null) {
                                        listOfNotNull(item.synced.lang, item.synced.pkgName).joinToString(" · ")
                                    } else {
                                        stringResource(MR.strings.webdav_sync_extension_not_in_stores)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                            when (item.installStep) {
                                InstallStep.Pending, InstallStep.Downloading -> {
                                    Text(
                                        text = stringResource(MR.strings.webdav_sync_extension_downloading),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                InstallStep.Installing -> {
                                    Text(
                                        text = stringResource(MR.strings.webdav_sync_extension_installing),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                InstallStep.Installed -> {
                                    Text(
                                        text = stringResource(MR.strings.webdav_sync_extension_installed),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                InstallStep.Idle, InstallStep.Error -> {
                                    TextButton(
                                        enabled = item.available != null,
                                        onClick = { onInstall(item) },
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (item.installStep == InstallStep.Error) {
                                                    MR.strings.webdav_sync_extension_install_error
                                                } else {
                                                    MR.strings.webdav_sync_install
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasInstallable) {
                TextButton(onClick = onInstallAll) {
                    Text(text = stringResource(MR.strings.webdav_sync_install_all))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun intervalLabel(minutes: Int): String {
    return when (minutes) {
        0 -> stringResource(MR.strings.webdav_sync_interval_off)
        15 -> stringResource(MR.strings.webdav_sync_interval_15m)
        30 -> stringResource(MR.strings.webdav_sync_interval_30m)
        60 -> stringResource(MR.strings.webdav_sync_interval_1h)
        180 -> stringResource(MR.strings.webdav_sync_interval_3h)
        360 -> stringResource(MR.strings.webdav_sync_interval_6h)
        720 -> stringResource(MR.strings.webdav_sync_interval_12h)
        1440 -> stringResource(MR.strings.webdav_sync_interval_24h)
        else -> stringResource(MR.strings.webdav_sync_interval_1h)
    }
}
