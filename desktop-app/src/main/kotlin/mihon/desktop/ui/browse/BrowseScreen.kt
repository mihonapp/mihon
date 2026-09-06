package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.InstalledExtension
import mihon.extension.model.SourceDescriptor

enum class BrowseTab {
    Sources,
    Extensions,
}

data class BrowseUiState(
    val selectedTab: BrowseTab = BrowseTab.Sources,
    val installedExtensions: List<InstalledExtension> = emptyList(),
    val availableExtensions: List<ExtensionStoreItem> = emptyList(),
    val repositories: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
)

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onTabSelected: (BrowseTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSourceSelected: (SourceDescriptor) -> Unit,
    onInstallExtension: (ExtensionStoreItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onToggleExtensionEnabled: (String, Boolean) -> Unit,
    onAddRepository: (String) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onRefresh: () -> Unit = {},
) {
    var showRepoDialog by remember { mutableStateOf(false) }
    var pendingInstallItem: ExtensionStoreItem? by remember { mutableStateOf(null) }
    val strings = mihon.desktop.i18n.LocalStrings.current

    Column(modifier = Modifier.fillMaxSize().testTag("browse-screen")) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.browseTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Row {
                OutlinedButton(
                    onClick = { showRepoDialog = true },
                    modifier = Modifier.testTag("manage-repos-button"),
                ) {
                    Text(strings.browseManageRepositories)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh-browse-button"),
                ) {
                    Text(strings.browseRefresh)
                }
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = state.selectedTab == BrowseTab.Sources,
                onClick = { onTabSelected(BrowseTab.Sources) },
                text = { Text(strings.browseTabSources) },
                modifier = Modifier.testTag("browse-tab-sources"),
            )
            Tab(
                selected = state.selectedTab == BrowseTab.Extensions,
                onClick = { onTabSelected(BrowseTab.Extensions) },
                text = { Text("${strings.browseTabExtensions} (${state.availableExtensions.size})") },
                modifier = Modifier.testTag("browse-tab-extensions"),
            )
        }

        // Search Bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("browse-search-bar"),
            placeholder = { Text("Search sources or extensions...") },
            singleLine = true,
        )

        // Error Banner
        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Content
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("browse-loading-indicator"))
            }
        } else {
            when (state.selectedTab) {
                BrowseTab.Sources -> SourcesListView(state, onSourceSelected)
                BrowseTab.Extensions -> ExtensionsListView(
                    state = state,
                    onRequestInstall = { item -> pendingInstallItem = item },
                    onUninstallExtension = onUninstallExtension,
                    onToggleEnabled = onToggleExtensionEnabled,
                )
            }
        }
    }

    // Security Confirmation Dialog for Extension Installation
    pendingInstallItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingInstallItem = null },
            title = { Text("Install Extension: " + item.name) },
            text = {
                Column {
                    Text("Package: " + item.pkg)
                    Text("Version: " + item.version)
                    Text("Language: " + item.lang)
                    if (item.declaredDomains.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Declared Network Domains:",
                            fontWeight = FontWeight.SemiBold,
                        )
                        item.declaredDomains.forEach { domain ->
                            Text(" - " + domain, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Caution: Only install extensions from repositories you trust. " +
                            "Extensions run in a sandboxed host with brokered network access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toInstall = item
                        pendingInstallItem = null
                        onInstallExtension(toInstall)
                    },
                    modifier = Modifier.testTag("confirm-install-button"),
                ) {
                    Text("Trust & Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstallItem = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Repository Management Dialog
    if (showRepoDialog) {
        var newRepoUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRepoDialog = false },
            title = { Text("Manage Extension Repositories") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newRepoUrl,
                            onValueChange = { newRepoUrl = it },
                            placeholder = { Text("https://example.com/repo") },
                            modifier = Modifier.weight(1f).testTag("new-repo-input"),
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newRepoUrl.isNotBlank()) {
                                    onAddRepository(newRepoUrl.trim())
                                    newRepoUrl = ""
                                }
                            },
                            modifier = Modifier.testTag("add-repo-button"),
                        ) {
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Configured Repositories:", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.repositories) { repo ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = repo,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    onClick = { onRemoveRepository(repo) },
                                    modifier = Modifier.testTag("remove-repo-" + repo),
                                ) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRepoDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun SourcesListView(
    state: BrowseUiState,
    onSourceSelected: (SourceDescriptor) -> Unit,
) {
    val enabledSources = state.installedExtensions
        .filter { it.isEnabled }
        .flatMap { it.manifest.sources }
        .filter { source ->
            state.searchQuery.isBlank() ||
                source.name.contains(state.searchQuery, ignoreCase = true) ||
                source.lang.contains(state.searchQuery, ignoreCase = true)
        }

    if (enabledSources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (state.installedExtensions.isEmpty()) {
                    "No extensions installed. Go to the Extensions tab to install sources."
                } else {
                    "No sources matching query."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("sources-list")) {
            items(enabledSources, key = { it.id }) { source ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSourceSelected(source) }
                        .padding(16.dp)
                        .testTag("source-item-" + source.id),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Lang: " + source.lang.uppercase() + " (ID: " + source.id + ")",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    OutlinedButton(onClick = { onSourceSelected(source) }) {
                        Text("Browse")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ExtensionsListView(
    state: BrowseUiState,
    onRequestInstall: (ExtensionStoreItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
) {
    val installedPkgMap = state.installedExtensions.associateBy { it.pkg }
    val filtered = state.availableExtensions.filter { item ->
        state.searchQuery.isBlank() ||
            item.name.contains(state.searchQuery, ignoreCase = true) ||
            item.pkg.contains(state.searchQuery, ignoreCase = true) ||
            item.lang.contains(state.searchQuery, ignoreCase = true)
    }

    LazyColumn(modifier = Modifier.fillMaxSize().testTag("extensions-list")) {
        items(filtered, key = { it.pkg }) { item ->
            val installed = installedPkgMap[item.pkg]
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("extension-item-" + item.pkg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "v" + item.version,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = item.pkg + " (" + item.lang.uppercase() + ")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (item.sources.isNotEmpty()) {
                        Text(
                            text = "Sources: " + item.sources.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (installed == null) {
                        Button(
                            onClick = { onRequestInstall(item) },
                            modifier = Modifier.testTag("install-btn-" + item.pkg),
                        ) {
                            Text("Install")
                        }
                    } else {
                        // Installed: Show enable switch and uninstall button
                        OutlinedButton(
                            onClick = { onToggleEnabled(item.pkg, !installed.isEnabled) },
                            modifier = Modifier.testTag("toggle-btn-" + item.pkg),
                        ) {
                            Text(if (installed.isEnabled) "Disable" else "Enable")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { onUninstallExtension(item.pkg) },
                            modifier = Modifier.testTag("uninstall-btn-" + item.pkg),
                        ) {
                            Text("Uninstall", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
