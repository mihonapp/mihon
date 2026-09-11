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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourceState
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.model.LibraryManga
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import java.io.File

enum class BrowseTab {
    Sources,
    Extensions,
    Migration,
}

data class BrowseUiState(
    val selectedTab: BrowseTab = BrowseTab.Sources,
    val installedExtensions: List<InstalledExtension> = emptyList(),
    val availableExtensions: List<ExtensionStoreItem> = emptyList(),
    val repositories: List<String> = emptyList(),
    val sources: List<SourceDescriptor> = emptyList(),
    val sourceStates: List<SourceState> = emptyList(),
    val extensionSourceStates: Map<String, List<SourceState>> = emptyMap(),
    val incognitoExtensionPackages: Set<String> = emptySet(),
    val sourcePreferenceDefinitions: Map<Long, List<SourcePreferenceDefinition>> = emptyMap(),
    val sourcePreferenceValues: Map<Long, Map<String, String>> = emptyMap(),
    val pinnedSourceIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val isInstalling: Boolean = false,
    val installingPkg: String? = null,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    // Migration state
    val sourcesWithMangaCounts: List<SourceWithMangaCount> = emptyList(),
    val selectedMigrationSource: SourceWithMangaCount? = null,
    val mangasForSelectedMigrationSource: List<LibraryManga> = emptyList(),
    // Global search
    val isGlobalSearchOpen: Boolean = false,
    val globalSearchQuery: String = "",
    val isGlobalSearching: Boolean = false,
    val globalSearchResults: List<GlobalSearchSourceResult> = emptyList(),
)

fun chooseMextFile(): File? {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        "Select Extension Package (.mext, .apk, .jar)",
        java.awt.FileDialog.LOAD,
    )
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".mext", ignoreCase = true) ||
            name.endsWith(".apk", ignoreCase = true) ||
            name.endsWith(".jar", ignoreCase = true)
    }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return File(dir, file)
}

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onTabSelected: (BrowseTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit = { _, _ -> },
    onTogglePinSource: (Long) -> Unit = {},
    onInstallExtension: (ExtensionStoreItem) -> Unit = {},
    onInstallFromFile: (File) -> Unit = {},
    onUninstallExtension: (String) -> Unit = {},
    onToggleExtensionEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onExtensionSelected: (InstalledExtension) -> Unit = {},
    onAddRepository: (String) -> Unit = {},
    onRemoveRepository: (String) -> Unit = {},
    onUpdateAllPending: () -> Unit = {},
    onRefresh: () -> Unit = {},
    // Global search handlers
    onOpenGlobalSearch: () -> Unit = {},
    onCloseGlobalSearch: () -> Unit = {},
    onGlobalSearchQueryChange: (String) -> Unit = {},
    onPerformGlobalSearch: () -> Unit = {},
    onGlobalMangaSelected: (SourceDescriptor, SManga) -> Unit = { _, _ -> },
    // Migration handlers
    onSelectMigrationSource: (SourceWithMangaCount?) -> Unit = {},
    onSearchTargetMigrationSource: suspend (sourceId: Long, query: String) -> List<SManga> = { _, _ -> emptyList() },
    onPerformMigration: (
        oldManga: LibraryManga,
        targetSource: SourceDescriptor,
        targetManga: SManga,
    ) -> Unit = { _, _, _ -> },
    // Extension trust state/actions
    extensionTrustStatuses: Map<String, ExtensionTrustStatus>? = null,
    trustStatuses: Map<String, ExtensionTrustStatus>? = extensionTrustStatuses,
    extensionTrustStates: Map<String, ExtensionTrustStatus>? = null,
    trustStates: Map<String, ExtensionTrustStatus>? = extensionTrustStates,
    onTrustExtension: ((InstalledExtension) -> Unit)? = null,
    onTrust: ((InstalledExtension) -> Unit)? = onTrustExtension,
    onRevokeExtension: ((InstalledExtension) -> Unit)? = null,
    onRevoke: ((InstalledExtension) -> Unit)? = onRevokeExtension,
    onTrustPackage: ((String) -> Unit)? = null,
    onRevokePackage: ((String) -> Unit)? = null,
) {
    if (state.isGlobalSearchOpen) {
        GlobalSearchScreen(
            query = state.globalSearchQuery,
            onQueryChange = onGlobalSearchQueryChange,
            onSearch = onPerformGlobalSearch,
            onBack = onCloseGlobalSearch,
            isSearching = state.isGlobalSearching,
            sourceResults = state.globalSearchResults,
            onMangaSelected = onGlobalMangaSelected,
            onViewSource = { source ->
                onCloseGlobalSearch()
                onSourceSelected(source, SourceListingMode.Popular)
            },
        )
        return
    }

    var showRepoDialog by remember { mutableStateOf(false) }
    var pendingInstallItem: ExtensionStoreItem? by remember { mutableStateOf(null) }
    val strings = LocalStrings.current

    // Compute update count
    val installedPkgMap = state.installedExtensions.associateBy { it.pkg }
    val pendingUpdates = state.availableExtensions.filter { available ->
        val installed = installedPkgMap[available.pkg]
        installed != null && available.versionCode > installed.manifest.versionCode
    }

    // Trust state comes from the caller when provided, otherwise from the active trust store.
    val activeTrustStore = remember { ExtensionTrustStore.active() }
    val trustRevision = activeTrustStore?.revision?.collectAsState()?.value ?: 0L
    val pendingTrustRequest = activeTrustStore?.pendingTrustRequest?.collectAsState()?.value
    val computedTrustStatuses = remember(trustRevision, state.installedExtensions) {
        state.installedExtensions.associate { installed ->
            val fingerprints = installed.signatureFingerprints.ifEmpty {
                listOfNotNull(installed.signatureFingerprint.takeIf { it.isNotBlank() })
            }
            val storeStatus = activeTrustStore?.status(
                pkg = installed.pkg,
                fingerprints = fingerprints,
                trustedSigningKeys = listOfNotNull(installed.signingKey.takeIf { it.isNotBlank() }),
                signatureValid = installed.trustStatus != ExtensionTrustStatus.INVALID,
            )
            val storeHasOpinion = activeTrustStore != null && (
                activeTrustStore.getTrustedFingerprints(installed.pkg).isNotEmpty() ||
                    activeTrustStore.isRevoked(installed.pkg) ||
                    ExtensionTrustStore.normalizeFingerprint(installed.signingKey).isNotBlank()
                )
            installed.pkg to if (storeHasOpinion) (storeStatus ?: installed.trustStatus) else installed.trustStatus
        }
    }
    val providedTrustStatuses = extensionTrustStatuses
        ?: trustStatuses
        ?: extensionTrustStates
        ?: trustStates
    val effectiveTrustStatuses = if (providedTrustStatuses == null) {
        computedTrustStatuses
    } else {
        computedTrustStatuses + providedTrustStatuses
    }
    val handleTrust: (InstalledExtension) -> Unit = onTrust
        ?: onTrustExtension
        ?: onTrustPackage?.let { callback -> { installed -> callback(installed.pkg) } }
        ?: { installed ->
            val store = activeTrustStore ?: ExtensionTrustStore.active()
            val fingerprints = installed.signatureFingerprints.ifEmpty {
                listOfNotNull(
                    installed.signatureFingerprint.takeIf { it.isNotBlank() },
                    installed.signingKey.takeIf { it.isNotBlank() },
                )
            }
            if (fingerprints.isEmpty()) {
                store?.trustUnsigned(installed.pkg)
            } else {
                store?.trust(installed.pkg, fingerprints)
            }
        }
    val handleRevoke: (InstalledExtension) -> Unit = onRevoke
        ?: onRevokeExtension
        ?: onRevokePackage?.let { callback -> { installed -> callback(installed.pkg) } }
        ?: { installed -> (activeTrustStore ?: ExtensionTrustStore.active())?.revoke(installed.pkg) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("browse-screen")) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.browseTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onOpenGlobalSearch,
                    modifier = Modifier.testTag("open-global-search-button"),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.browseGlobalSearch)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val file = chooseMextFile()
                        if (file != null) {
                            onInstallFromFile(file)
                        }
                    },
                    modifier = Modifier.testTag("install-file-button"),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.browseInstallFromFile)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showRepoDialog = true },
                    modifier = Modifier.testTag("manage-repos-button"),
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.browseManageRepositories)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh-browse-button"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
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
                text = { Text("${strings.browseTabSources} (${state.sources.size})") },
                modifier = Modifier.testTag("browse-tab-sources"),
            )
            Tab(
                selected = state.selectedTab == BrowseTab.Extensions,
                onClick = { onTabSelected(BrowseTab.Extensions) },
                text = {
                    if (pendingUpdates.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text(pendingUpdates.size.toString()) } }) {
                            Text("${strings.browseTabExtensions} (${state.availableExtensions.size})")
                        }
                    } else {
                        Text("${strings.browseTabExtensions} (${state.availableExtensions.size})")
                    }
                },
                modifier = Modifier.testTag("browse-tab-extensions"),
            )
            Tab(
                selected = state.selectedTab == BrowseTab.Migration,
                onClick = { onTabSelected(BrowseTab.Migration) },
                text = { Text(strings.browseTabMigration) },
                modifier = Modifier.testTag("browse-tab-migration"),
            )
        }

        // Filter / Search Bar (for Sources & Extensions tabs)
        if (state.selectedTab != BrowseTab.Migration) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("browse-search-bar"),
                placeholder = { Text(strings.browseSearchPlaceholder) },
                singleLine = true,
            )
        }

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
                BrowseTab.Sources -> SourcesListView(
                    sources = state.sources,
                    pinnedIds = state.pinnedSourceIds,
                    searchQuery = state.searchQuery,
                    installedExtensions = state.installedExtensions,
                    onSourceSelected = onSourceSelected,
                    onTogglePin = onTogglePinSource,
                    onExtensionSelected = onExtensionSelected,
                )
                BrowseTab.Extensions -> ExtensionsListView(
                    state = state,
                    pendingUpdates = pendingUpdates,
                    trustStatuses = effectiveTrustStatuses,
                    onRequestInstall = { item -> pendingInstallItem = item },
                    onUninstallExtension = onUninstallExtension,
                    onToggleEnabled = onToggleExtensionEnabled,
                    onExtensionSelected = onExtensionSelected,
                    onUpdateAllPending = onUpdateAllPending,
                    onTrustExtension = handleTrust,
                    onRevokeExtension = handleRevoke,
                )
                BrowseTab.Migration -> MigrateSourceScreen(
                    sourcesWithCounts = state.sourcesWithMangaCounts,
                    selectedSource = state.selectedMigrationSource,
                    mangasForSelectedSource = state.mangasForSelectedMigrationSource,
                    availableTargetSources = state.sources.filterNot { it.id == BundledLocalSource.ID },
                    onSelectSource = { onSelectMigrationSource(it) },
                    onBackToSourceList = { onSelectMigrationSource(null) },
                    onSearchTargetSource = onSearchTargetMigrationSource,
                    onPerformMigration = onPerformMigration,
                )
            }
        }
    }

    // Security Confirmation Dialog for Extension Installation
    pendingInstallItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingInstallItem = null },
            title = { Text(strings.browseInstallExtensionTitle(item.name)) },
            text = {
                Column {
                    Text(strings.browsePackageLabel(item.pkg))
                    Text(strings.browseVersionLabel(item.version))
                    Text(strings.browseLanguageLabel(item.lang.uppercase()))
                    if (item.declaredDomains.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.browseNetworkDomainsHeader,
                            fontWeight = FontWeight.SemiBold,
                        )
                        item.declaredDomains.forEach { domain ->
                            Text(" • $domain", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val usableSigningKey = ExtensionTrustStore.normalizeFingerprint(item.signingKey)
                    if (usableSigningKey.isNotBlank()) {
                        Text(
                            text = "Repository signing key: $usableSigningKey",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            text = "No repository signing key. You must explicitly trust this package's signature.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("unsigned-extension-warning"),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.browseNetworkPermissionNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // The confirmation dialog is the explicit user trust action required for
                        // packages whose repository does not publish a signing key.
                        val toInstall = item.copy(trustOnInstall = true)
                        pendingInstallItem = null
                        onInstallExtension(toInstall)
                    },
                    modifier = Modifier.testTag("confirm-install-button"),
                ) {
                    Text(strings.browseTrustAndInstall)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstallItem = null }) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }

    // Trust-required retry dialog. The installer records a pending request when it blocks an
    // install on an unknown signer; confirming here persists explicit trust and retries the
    // original install without requiring presenter changes.
    val trustError = state.errorMessage
    if (pendingTrustRequest != null &&
        trustError != null &&
        trustError.contains("Failed to install", ignoreCase = true) &&
        (trustError.contains("trust", ignoreCase = true) || trustError.contains("signature", ignoreCase = true))
    ) {
        val request = pendingTrustRequest
        AlertDialog(
            onDismissRequest = { activeTrustStore?.clearPendingTrustRequest() },
            title = { Text("Trust extension signature?") },
            text = {
                Column {
                    Text("Package: ${request.pkg}")
                    Text(
                        text = if (request.fingerprints.isEmpty()) {
                            "This package is unsigned. Trusting it allows the extension to run."
                        } else {
                            "Signer SHA-256: ${request.fingerprints.joinToString()}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (request.reason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(request.reason, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeTrustStore?.clearPendingTrustRequest()
                        if (request.fingerprints.isEmpty()) {
                            activeTrustStore?.trustUnsigned(request.pkg)
                        } else {
                            activeTrustStore?.trust(request.pkg, request.fingerprints)
                        }
                        val storeItem = request.storeItem
                        if (storeItem != null) {
                            onInstallExtension(storeItem.copy(trustOnInstall = true))
                        } else {
                            request.filePath?.let { path -> onInstallFromFile(File(path)) }
                        }
                    },
                    modifier = Modifier.testTag("confirm-trust-install-button"),
                ) {
                    Text("Trust & Install")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeTrustStore?.clearPendingTrustRequest() },
                    modifier = Modifier.testTag("cancel-trust-button"),
                ) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }

    // Repository Management Dialog
    if (showRepoDialog) {
        var newRepoUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRepoDialog = false },
            title = { Text(strings.browseManageRepositories) },
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
                            Text(strings.browseAddRepository)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(strings.browseConfiguredRepositories, fontWeight = FontWeight.SemiBold)
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
                                    modifier = Modifier.testTag("remove-repo-$repo"),
                                ) {
                                    Text(strings.browseRemoveRepository, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRepoDialog = false }) {
                    Text(strings.dialogClose)
                }
            },
        )
    }
}

@Composable
private fun SourcesListView(
    sources: List<SourceDescriptor>,
    pinnedIds: Set<Long>,
    searchQuery: String,
    installedExtensions: List<InstalledExtension> = emptyList(),
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit,
    onTogglePin: (Long) -> Unit,
    onExtensionSelected: (InstalledExtension) -> Unit = {},
) {
    val strings = LocalStrings.current
    val filteredSources = sources.filter { source ->
        searchQuery.isBlank() ||
            source.name.contains(searchQuery, ignoreCase = true) ||
            source.lang.contains(searchQuery, ignoreCase = true)
    }

    val pinnedSources = filteredSources.filter { pinnedIds.contains(it.id) }
    val otherSources = filteredSources.filterNot { pinnedIds.contains(it.id) }

    if (filteredSources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = strings.browseNoMangaFound,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("sources-list")) {
            // Pinned Section
            if (pinnedSources.isNotEmpty()) {
                item {
                    Text(
                        text = strings.browsePin.replace("☆", "").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(pinnedSources, key = { "pinned_${it.id}" }) { source ->
                    SourceListItem(
                        source = source,
                        isPinned = true,
                        installedExtensions = installedExtensions,
                        onSourceSelected = onSourceSelected,
                        onTogglePin = onTogglePin,
                        onExtensionSelected = onExtensionSelected,
                    )
                    HorizontalDivider()
                }
            }

            // All/Other Sources Grouped by Lang
            val grouped = otherSources.groupBy { it.lang.uppercase() }
            grouped.forEach { (lang, langSources) ->
                item {
                    Text(
                        text = strings.browseSourceLanguage(lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(langSources, key = { it.id }) { source ->
                    SourceListItem(
                        source = source,
                        isPinned = false,
                        installedExtensions = installedExtensions,
                        onSourceSelected = onSourceSelected,
                        onTogglePin = onTogglePin,
                        onExtensionSelected = onExtensionSelected,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SourceListItem(
    source: SourceDescriptor,
    isPinned: Boolean,
    installedExtensions: List<InstalledExtension> = emptyList(),
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit,
    onTogglePin: (Long) -> Unit,
    onExtensionSelected: (InstalledExtension) -> Unit = {},
) {
    val strings = LocalStrings.current
    val associatedExtension = remember(source.id, installedExtensions) {
        installedExtensions.find { ext -> ext.manifest.sources.any { it.id == source.id } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSourceSelected(source, SourceListingMode.Popular) }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag("source-item-${source.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val builtinBadge = when (source.id) {
                    2499283573021220255L -> "[Built-in]"
                    BundledLocalSource.ID -> "[Local]"
                    else -> null
                }
                if (builtinBadge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = builtinBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "${strings.browseSourceLanguage(source.lang)} • ID: ${source.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (associatedExtension != null) {
                IconButton(
                    onClick = { onExtensionSelected(associatedExtension) },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("source-ext-btn-${source.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = strings.extensionInfo,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = { onSourceSelected(source, SourceListingMode.Latest) },
                enabled = source.supportsLatest,
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text(strings.browseSourceLatest)
            }
            Button(
                onClick = { onSourceSelected(source, SourceListingMode.Popular) },
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text(strings.browseSourceBrowse)
            }
            TextButton(
                onClick = { onTogglePin(source.id) },
                modifier = Modifier.testTag("pin-btn-${source.id}"),
            ) {
                Text(if (isPinned) strings.browseUnpin else strings.browsePin)
            }
        }
    }
}

@Composable
private fun ExtensionsListView(
    state: BrowseUiState,
    pendingUpdates: List<ExtensionStoreItem>,
    trustStatuses: Map<String, ExtensionTrustStatus>,
    onRequestInstall: (ExtensionStoreItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onExtensionSelected: (InstalledExtension) -> Unit,
    onUpdateAllPending: () -> Unit,
    onTrustExtension: (InstalledExtension) -> Unit,
    onRevokeExtension: (InstalledExtension) -> Unit,
) {
    val strings = LocalStrings.current
    val installedPkgMap = state.installedExtensions.associateBy { it.pkg }
    val filteredAvailable = state.availableExtensions.filter { item ->
        state.searchQuery.isBlank() ||
            item.name.contains(state.searchQuery, ignoreCase = true) ||
            item.pkg.contains(state.searchQuery, ignoreCase = true) ||
            item.lang.contains(state.searchQuery, ignoreCase = true)
    }

    LazyColumn(modifier = Modifier.fillMaxSize().testTag("extensions-list")) {
        // 1. Pending Updates Section
        if (pendingUpdates.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${strings.browseUpdate} (${pendingUpdates.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(onClick = onUpdateAllPending, modifier = Modifier.testTag("update-all-button")) {
                        Text(strings.browseUpdateAll)
                    }
                }
            }
            items(pendingUpdates, key = { "update_${it.pkg}" }) { item ->
                val installed = installedPkgMap[item.pkg]
                ExtensionItemRow(
                    item = item,
                    installed = installed,
                    trustStatus = trustStatuses[item.pkg],
                    isInstalling = state.isInstalling && state.installingPkg == item.pkg,
                    onRequestInstall = onRequestInstall,
                    onUninstallExtension = onUninstallExtension,
                    onToggleEnabled = onToggleEnabled,
                    onExtensionSelected = onExtensionSelected,
                    onTrustExtension = onTrustExtension,
                    onRevokeExtension = onRevokeExtension,
                )
                HorizontalDivider()
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // 2. Installed Extensions Section
        if (state.installedExtensions.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.browseInstalledBadge} (${state.installedExtensions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(state.installedExtensions, key = { "installed_${it.pkg}" }) { installed ->
                val availableMatch = state.availableExtensions.find { it.pkg == installed.pkg }
                val item = availableMatch ?: ExtensionStoreItem(
                    pkg = installed.pkg,
                    name = installed.manifest.name,
                    version = installed.manifest.version,
                    versionCode = installed.manifest.versionCode,
                    lang = installed.manifest.lang,
                    sources = installed.manifest.sources,
                )
                ExtensionItemRow(
                    item = item,
                    installed = installed,
                    trustStatus = trustStatuses[item.pkg],
                    isInstalling = state.isInstalling && state.installingPkg == item.pkg,
                    onRequestInstall = onRequestInstall,
                    onUninstallExtension = onUninstallExtension,
                    onToggleEnabled = onToggleEnabled,
                    onExtensionSelected = onExtensionSelected,
                    onTrustExtension = onTrustExtension,
                    onRevokeExtension = onRevokeExtension,
                )
                HorizontalDivider()
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // 3. Available Extensions (The Store)
        val notInstalled = filteredAvailable.filter { !installedPkgMap.containsKey(it.pkg) }
        val groupedAvailable = notInstalled.groupBy { it.lang.uppercase() }

        item {
            Text(
                text = "${strings.browseTabExtensions} (${notInstalled.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (notInstalled.isEmpty()) {
            item {
                Text(
                    text = if (state.availableExtensions.isEmpty()) {
                        "No extension repositories loaded. Add a repository or install a .mext file from your disk."
                    } else {
                        strings.browseNoMangaFound
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            groupedAvailable.forEach { (lang, items) ->
                item {
                    Text(
                        text = "${strings.browseSourceLanguage(lang)} (${items.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                items(items, key = { it.pkg }) { item ->
                    ExtensionItemRow(
                        item = item,
                        installed = null,
                        trustStatus = trustStatuses[item.pkg],
                        isInstalling = state.isInstalling && state.installingPkg == item.pkg,
                        onRequestInstall = onRequestInstall,
                        onUninstallExtension = onUninstallExtension,
                        onToggleEnabled = onToggleEnabled,
                        onExtensionSelected = onExtensionSelected,
                        onTrustExtension = onTrustExtension,
                        onRevokeExtension = onRevokeExtension,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ExtensionItemRow(
    item: ExtensionStoreItem,
    installed: InstalledExtension?,
    trustStatus: ExtensionTrustStatus?,
    isInstalling: Boolean,
    onRequestInstall: (ExtensionStoreItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onExtensionSelected: (InstalledExtension) -> Unit,
    onTrustExtension: (InstalledExtension) -> Unit,
    onRevokeExtension: (InstalledExtension) -> Unit,
) {
    val strings = LocalStrings.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = installed != null) { installed?.let(onExtensionSelected) }
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .testTag("extension-item-${item.pkg}"),
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
                        text = "v${item.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (item.isNsfw) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "18+",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = "${item.pkg} • ${item.lang.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (item.sources.isNotEmpty()) {
                    Text(
                        text = "Sources: ${item.sources.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else if (installed == null) {
                    Button(
                        onClick = { onRequestInstall(item) },
                        modifier = Modifier.testTag("install-btn-${item.pkg}"),
                    ) {
                        Text(strings.browseInstall)
                    }
                } else {
                    val hasUpdate = item.versionCode > installed.manifest.versionCode
                    if (hasUpdate) {
                        Button(
                            onClick = { onRequestInstall(item) },
                            modifier = Modifier.testTag("update-btn-${item.pkg}"),
                        ) {
                            Text(strings.browseUpdate)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = { onExtensionSelected(installed) },
                        modifier = Modifier.testTag("settings-btn-${item.pkg}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.extensionInfo,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { onToggleEnabled(item.pkg, !installed.isEnabled) },
                        modifier = Modifier.testTag("toggle-btn-${item.pkg}"),
                    ) {
                        Text(if (installed.isEnabled) strings.browseDisable else strings.browseEnable)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onUninstallExtension(item.pkg) },
                        modifier = Modifier.testTag("uninstall-btn-${item.pkg}"),
                    ) {
                        Text(strings.browseUninstall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        val displayTrustStatus = if (installed != null) {
            trustStatus ?: installed.trustStatus
        } else if (ExtensionTrustStore.normalizeFingerprint(item.signingKey).isNotBlank()) {
            ExtensionTrustStatus.TRUSTED
        } else {
            ExtensionTrustStatus.UNKNOWN
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayTrustStatus.label,
                style = MaterialTheme.typography.bodySmall,
                color = when (displayTrustStatus) {
                    ExtensionTrustStatus.TRUSTED -> MaterialTheme.colorScheme.primary
                    ExtensionTrustStatus.INVALID -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.testTag("extension-trust-status-${item.pkg}"),
            )
            if (installed != null) {
                TextButton(
                    onClick = { onTrustExtension(installed) },
                    modifier = Modifier.testTag("trust-btn-${item.pkg}"),
                ) {
                    Text("Trust")
                }
                TextButton(
                    onClick = { onRevokeExtension(installed) },
                    modifier = Modifier.testTag("revoke-btn-${item.pkg}"),
                ) {
                    Text("Revoke")
                }
            }
        }
    }
}
