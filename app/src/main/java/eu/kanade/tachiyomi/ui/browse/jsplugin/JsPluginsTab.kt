package eu.kanade.tachiyomi.ui.browse.jsplugin

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.jsplugin.model.JsPlugin
import eu.kanade.tachiyomi.ui.browse.jsplugin.JsPluginsScreenModel.JsPluginItem
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun jsPluginsTab(
    screenModel: JsPluginsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    val updateCount = screenModel.getUpdateCount()

    var showRepoDialog by remember { mutableStateOf(false) }

    return TabContent(
        titleRes = MR.strings.label_extensions, // TODO: Add proper string resource
        badgeNumber = updateCount.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { /* TODO: Language filter */ },
            ),
            AppBar.OverflowAction(
                title = "Manage Repositories", // TODO: Add string resource
                onClick = { showRepoDialog = true },
            ),
        ),
        content = { contentPadding, _ ->
            JsPluginsScreen(
                state = state,
                plugins = screenModel.getFilteredPlugins(),
                contentPadding = contentPadding,
                onRefresh = screenModel::refreshPlugins,
                onInstallPlugin = screenModel::installPlugin,
                onUninstallPlugin = screenModel::uninstallPlugin,
                onUpdatePlugin = screenModel::updatePlugin,
                onUpdateAll = screenModel::updateAllPlugins,
            )

            if (showRepoDialog) {
                ManageRepositoriesDialog(
                    repositories = state.repositories,
                    onAddRepository = { name, url -> 
                        screenModel.addRepository(name, url)
                    },
                    onRemoveRepository = screenModel::removeRepository,
                    onToggleRepository = screenModel::toggleRepository,
                    onDismiss = { showRepoDialog = false },
                )
            }
        },
    )
}

@Composable
private fun JsPluginsScreen(
    state: JsPluginsScreenModel.State,
    plugins: List<JsPluginItem>,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onInstallPlugin: (JsPlugin) -> Unit,
    onUninstallPlugin: (eu.kanade.tachiyomi.jsplugin.model.InstalledJsPlugin) -> Unit,
    onUpdatePlugin: (JsPlugin) -> Unit,
    onUpdateAll: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading && plugins.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (plugins.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Update all button if updates available
                val updateCount = plugins.count { it.hasUpdate }
                if (updateCount > 0) {
                    item {
                        UpdateAllButton(
                            count = updateCount,
                            onClick = onUpdateAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                items(
                    items = plugins,
                    key = { it.plugin.id },
                ) { item ->
                    JsPluginCard(
                        item = item,
                        onInstall = { onInstallPlugin(item.plugin) },
                        onUninstall = { item.installed?.let { onUninstallPlugin(it) } },
                        onUpdate = { onUpdatePlugin(item.plugin) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }

        // Refresh indicator
        if (state.isLoading && plugins.isNotEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding() + 8.dp),
            )
        }
    }
}

@Composable
private fun UpdateAllButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.Update, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Update all ($count)")
    }
}

@Composable
private fun JsPluginCard(
    item: JsPluginItem,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plugin = item.plugin
    val isInstalled = item.installed != null
    val hasUpdate = item.hasUpdate

    Card(
        modifier = modifier
            .combinedClickable(
                onClick = { /* TODO: Show details */ },
                onLongClick = {
                    if (isInstalled) onUninstall() else onInstall()
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(plugin.iconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = plugin.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Spacer(Modifier.width(12.dp))

            // Info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    // JS badge
                    JsBadge()
                    
                    if (hasUpdate) {
                        Spacer(Modifier.width(4.dp))
                        UpdateBadge()
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = plugin.site,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = plugin.lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = " • v${plugin.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isInstalled && item.installed?.plugin?.version != plugin.version) {
                        Text(
                            text = " → v${plugin.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Actions
            if (hasUpdate) {
                IconButton(onClick = onUpdate) {
                    Icon(
                        Icons.Outlined.Update,
                        contentDescription = "Update",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (isInstalled) {
                IconButton(onClick = onUninstall) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Uninstall",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                IconButton(onClick = onInstall) {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = "Install",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun JsBadge() {
    Surface(
        color = Color(0xFF4CAF50), // Green for JS
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = "JS",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun UpdateBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = "UPDATE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ManageRepositoriesDialog(
    repositories: List<eu.kanade.tachiyomi.jsplugin.model.JsPluginRepository>,
    onAddRepository: (String, String) -> Unit,
    onRemoveRepository: (eu.kanade.tachiyomi.jsplugin.model.JsPluginRepository) -> Unit,
    onToggleRepository: (eu.kanade.tachiyomi.jsplugin.model.JsPluginRepository) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("JS Plugin Repositories") },
        text = {
            LazyColumn {
                items(repositories) { repo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = repo.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        
                        IconButton(onClick = { onRemoveRepository(repo) }) {
                            Icon(Icons.Outlined.Delete, "Remove")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAddDialog = true }) {
                Text("Add Repository")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )

    if (showAddDialog) {
        AddRepositoryDialog(
            onAdd = { name, url ->
                onAddRepository(name, url)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun AddRepositoryDialog(
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Repository") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
