package mihon.desktop.ui.browse

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourceState
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.i18n.trustStatusLabel
import mihon.extension.model.SourceDescriptor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Desktop pixel-perfect replication of upstream Mihon's Extension Details screen.
 * Follows eu.kanade.presentation.browse.ExtensionDetailsScreen layout and behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailsScreen(
    extension: InstalledExtension,
    sources: List<SourceState>,
    isExtensionIncognito: Boolean,
    updateItem: ExtensionStoreItem?,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: (ExtensionStoreItem) -> Unit,
    onUninstall: () -> Unit,
    onClearCookies: () -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    onToggleSourceEnabled: (Long, Boolean) -> Unit,
    onToggleSourceIncognito: (Long, Boolean) -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onOpenSource: (SourceDescriptor) -> Unit = {},
    trustStatus: ExtensionTrustStatus? = null,
    extensionTrustStatus: ExtensionTrustStatus? = trustStatus,
    trustState: ExtensionTrustStatus? = null,
    extensionTrustState: ExtensionTrustStatus? = trustState,
    onTrust: (() -> Unit)? = null,
    onTrustExtension: ((InstalledExtension) -> Unit)? = null,
    onRevoke: (() -> Unit)? = null,
    onRevokeExtension: ((InstalledExtension) -> Unit)? = null,
    onTrustPackage: ((String) -> Unit)? = null,
    onRevokePackage: ((String) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val icon = remember(extension.iconPath) { loadExtensionIcon(extension.iconPath) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }

    val activeTrustStore = remember { ExtensionTrustStore.active() }
    val trustRevision = activeTrustStore?.revision?.collectAsState()?.value ?: 0L
    val extensionFingerprints = remember(
        extension.signatureFingerprints,
        extension.signatureFingerprint,
    ) {
        extension.signatureFingerprints.ifEmpty {
            listOfNotNull(extension.signatureFingerprint.takeIf { it.isNotBlank() })
        }
    }
    val effectiveTrustStatus = remember(
        extension.pkg,
        extensionFingerprints,
        extension.signingKey,
        extension.trustStatus,
        extensionTrustStatus,
        trustRevision,
    ) {
        val storeStatus = activeTrustStore?.status(
            pkg = extension.pkg,
            fingerprints = extensionFingerprints,
            trustedSigningKeys = listOfNotNull(extension.signingKey.takeIf { it.isNotBlank() }),
            signatureValid = extension.trustStatus != ExtensionTrustStatus.INVALID,
        )
        val storeHasOpinion = activeTrustStore != null && (
            activeTrustStore.getTrustedFingerprints(extension.pkg).isNotEmpty() ||
                activeTrustStore.isRevoked(extension.pkg) ||
                ExtensionTrustStore.normalizeFingerprint(extension.signingKey).isNotBlank()
            )
        (extensionTrustStatus ?: extensionTrustState)
            ?: if (storeHasOpinion) (storeStatus ?: extension.trustStatus) else extension.trustStatus
    }

    val trustAction: () -> Unit = onTrust
        ?: onTrustExtension?.let { callback -> { callback(extension) } }
        ?: onTrustPackage?.let { callback -> { callback(extension.pkg) } }
        ?: {
            val store = activeTrustStore ?: ExtensionTrustStore.active()
            val fingerprints = extensionFingerprints.ifEmpty {
                listOfNotNull(extension.signingKey.takeIf { it.isNotBlank() })
            }
            if (fingerprints.isEmpty()) {
                store?.trustUnsigned(extension.pkg)
            } else {
                store?.trust(extension.pkg, fingerprints)
            }
        }

    val revokeAction: () -> Unit = onRevoke
        ?: onRevokeExtension?.let { callback -> { callback(extension) } }
        ?: onRevokePackage?.let { callback -> { callback(extension.pkg) } }
        ?: {
            (activeTrustStore ?: ExtensionTrustStore.active())?.revoke(extension.pkg)
        }

    val repoUrl = remember(extension.repoUrl) {
        val raw = extension.repoUrl.trim()
        if (raw.isBlank()) return@remember null
        val regex = """https://raw\.githubusercontent\.com/(.+?)/(.+?)/.+""".toRegex()
        regex.find(raw)?.let {
            val (user, repo) = it.destructured
            "https://github.com/$user/$repo"
        } ?: raw
    }

    var showMenu by remember { mutableStateOf(false) }
    var showNsfwWarning by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("extension-details-screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.extensionInfo,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("extension-details-back"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.mangaDetailBack,
                        )
                    }
                },
                actions = {
                    if (repoUrl != null) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    if (java.awt.Desktop.isDesktopSupported()) {
                                        java.awt.Desktop.getDesktop().browse(URI(repoUrl))
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = strings.extensionOpenRepo,
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = strings.text(UiText.More),
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.extensionEnableAll) },
                                onClick = {
                                    showMenu = false
                                    onToggleEnabled(true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(strings.extensionDisableAll) },
                                onClick = {
                                    showMenu = false
                                    onToggleEnabled(false)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(strings.extensionClearCookies) },
                                onClick = {
                                    showMenu = false
                                    onClearCookies()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(strings.extensionCookiesCleared)
                                    }
                                },
                            )
                            if (effectiveTrustStatus == ExtensionTrustStatus.TRUSTED || onRevoke != null) {
                                DropdownMenuItem(
                                    text = { Text(strings.extensionRevoke) },
                                    onClick = {
                                        showMenu = false
                                        revokeAction()
                                    },
                                )
                            }
                            if (effectiveTrustStatus != ExtensionTrustStatus.TRUSTED || onTrust != null) {
                                DropdownMenuItem(
                                    text = { Text(strings.extensionTrust) },
                                    onClick = {
                                        showMenu = false
                                        trustAction()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("extension-details-content"),
        ) {
            // 1. Obsolete or Untrusted warning banner
            if (extension.manifest.libVersion < 1.0) {
                item {
                    WarningBanner(text = strings.extensionObsolete)
                }
            }
            if (effectiveTrustStatus == ExtensionTrustStatus.UNTRUSTED) {
                item {
                    WarningBanner(text = strings.extensionUntrusted)
                }
            }

            // 2. Details Header
            item {
                DetailsHeader(
                    extension = extension,
                    icon = icon,
                    effectiveTrustStatus = effectiveTrustStatus,
                    isExtensionIncognito = isExtensionIncognito,
                    updateItem = updateItem,
                    sources = sources,
                    dateFormatter = dateFormatter,
                    onClickAgeRating = { showNsfwWarning = true },
                    onClickUninstall = onUninstall,
                    onClickUpdate = { updateItem?.let(onUpdate) },
                    onToggleEnabled = onToggleEnabled,
                    onClearCookies = onClearCookies,
                    onOpenSourcePreferences = onOpenSourcePreferences,
                    onExtIncognitoChange = onToggleIncognito,
                    onTrustClick = trustAction,
                    onRevokeClick = revokeAction,
                    showTrustButton =
                    onTrust != null || onTrustExtension != null || onTrustPackage != null ||
                        effectiveTrustStatus != ExtensionTrustStatus.TRUSTED,
                    showRevokeButton =
                    onRevoke != null || onRevokeExtension != null || onRevokePackage != null ||
                        effectiveTrustStatus == ExtensionTrustStatus.TRUSTED,
                    onCopyDebugInfo = {
                        val extDebugInfo = buildString {
                            appendLine(
                                "Extension name: ${extension.manifest.name} (lang: ${extension.manifest.lang}; package: ${extension.pkg})",
                            )
                            appendLine(
                                "Extension version: ${extension.manifest.version} (lib: ${extension.manifest.libVersion}; version code: ${extension.manifest.versionCode})",
                            )
                            appendLine("NSFW: ${extension.manifest.isNsfw}")
                            appendLine("Enabled: ${extension.isEnabled}")
                            appendLine("Trust status: ${effectiveTrustStatus.label}")
                            if (extension.repoUrl.isNotBlank()) {
                                appendLine("Repository: ${extension.repoUrl}")
                            }
                        }
                        runCatching {
                            val selection = StringSelection(extDebugInfo)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        }
                        scope.launch {
                            snackbarHostState.showSnackbar(strings.extensionDebugInfoCopied)
                        }
                    },
                )
            }

            // 3. Sources Section Header
            item {
                Text(
                    text = "${strings.browseTabSources} (${sources.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // 4. Source Items
            if (sources.isEmpty()) {
                item {
                    Text(
                        text = strings.text(UiText.NoExtensionSources),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("extension-details-no-sources"),
                    )
                }
            } else {
                items(
                    items = sources,
                    key = { it.source.id },
                ) { source ->
                    SourceSwitchPreference(
                        source = source,
                        onClickSourcePreferences = onOpenSourcePreferences,
                        onClickSource = onOpenSource,
                        onToggleEnabled = { enabled -> onToggleSourceEnabled(source.source.id, enabled) },
                        onToggleIncognito = { incognito -> onToggleSourceIncognito(source.source.id, incognito) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showNsfwWarning) {
        NsfwWarningDialog(
            onClickConfirm = { showNsfwWarning = false },
        )
    }
}

@Composable
private fun DetailsHeader(
    extension: InstalledExtension,
    icon: ImageBitmap?,
    effectiveTrustStatus: ExtensionTrustStatus,
    isExtensionIncognito: Boolean,
    updateItem: ExtensionStoreItem?,
    sources: List<SourceState>,
    dateFormatter: DateTimeFormatter,
    onClickAgeRating: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onExtIncognitoChange: (Boolean) -> Unit,
    onCopyDebugInfo: () -> Unit,
    onTrustClick: () -> Unit,
    onRevokeClick: () -> Unit,
    showTrustButton: Boolean,
    showRevokeButton: Boolean,
) {
    val strings = LocalStrings.current

    Column {
        // Icon and Title Header (icon is clickable to copy debug info)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onCopyDebugInfo)
                        .testTag("extension-details-icon"),
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onCopyDebugInfo)
                        .testTag("extension-details-icon"),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = extension.manifest.name.take(1).uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = extension.manifest.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("extension-details-name"),
            )

            val strippedPkgName = extension.pkg.substringAfter("eu.kanade.tachiyomi.extension.")

            Text(
                text = strippedPkgName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("extension-details-package"),
            )
        }

        // Info Row: Version | Language | Age Rating
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoText(
                modifier = Modifier.weight(1f),
                primaryText = extension.manifest.version,
                secondaryText = strings.extensionVersion,
                primaryTag = "extension-details-version",
            )

            InfoDivider()

            InfoText(
                modifier = Modifier.weight(if (extension.manifest.isNsfw) 1.5f else 1f),
                primaryText = LocaleHelper.getSourceDisplayName(extension.manifest.lang),
                secondaryText = strings.extensionLanguage,
                primaryTag = "extension-details-language",
            )

            if (extension.manifest.isNsfw) {
                InfoDivider()

                InfoText(
                    modifier = Modifier.weight(1f),
                    primaryText = strings.extensionNsfwShort,
                    primaryTextStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    ),
                    secondaryText = strings.extensionAgeRating,
                    primaryTag = "extension-details-nsfw",
                    onClick = onClickAgeRating,
                )
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("extension-details-uninstall"),
                onClick = onClickUninstall,
            ) {
                Text(strings.extensionUninstall)
            }

            if (updateItem != null) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("extension-details-update"),
                    onClick = onClickUpdate,
                ) {
                    Text(strings.extensionUpdate)
                }
            }

            if (showTrustButton) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("extension-details-trust"),
                    onClick = onTrustClick,
                ) {
                    Text(strings.extensionTrust)
                }
            }

            if (showRevokeButton) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("extension-details-revoke"),
                    onClick = onRevokeClick,
                ) {
                    Text(strings.extensionRevoke)
                }
            }

            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("extension-details-toggle-enabled"),
                onClick = { onToggleEnabled(!extension.isEnabled) },
            ) {
                Text(if (extension.isEnabled) "Disable" else "Enable")
            }

            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("extension-details-clear-cookies"),
                onClick = onClearCookies,
            ) {
                Text(strings.extensionClearCookies)
            }

            if (sources.isNotEmpty()) {
                OutlinedButton(
                    modifier = Modifier.testTag("extension-details-source-preferences"),
                    onClick = { sources.firstOrNull()?.source?.id?.let(onOpenSourcePreferences) },
                ) {
                    Text(strings.sourcePreferencesTitle)
                }
            }
        }

        // Incognito Mode Preference
        TextPreferenceWidget(
            modifier = Modifier.padding(horizontal = 8.dp),
            title = strings.extensionIncognitoMode,
            subtitle = strings.extensionIncognitoSummary,
            icon = Icons.Rounded.VisibilityOff,
            widget = {
                Switch(
                    checked = isExtensionIncognito,
                    onCheckedChange = onExtIncognitoChange,
                    modifier = Modifier.testTag("extension-details-incognito"),
                )
            },
            onPreferenceClick = { onExtIncognitoChange(!isExtensionIncognito) },
        )

        // Metadata Tags / Labels for Compatibility and Trust Store
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (extension.isEnabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.testTag("extension-details-enabled"),
            )
            Text(
                text = "•",
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = strings.text(UiText.TrustState, strings.trustStatusLabel(effectiveTrustStatus)),
                style = MaterialTheme.typography.labelMedium,
                color = when (effectiveTrustStatus) {
                    ExtensionTrustStatus.TRUSTED -> MaterialTheme.colorScheme.primary
                    ExtensionTrustStatus.INVALID -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.testTag("extension-details-trust-label"),
            )
            Text(
                text = strings.trustStatusLabel(effectiveTrustStatus),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.testTag("extension-details-trust-status"),
            )
            if (extension.installedAt > 0L) {
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = dateFormatter.format(Instant.ofEpochMilli(extension.installedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("extension-details-install-date"),
                )
            }
            if (extension.repoUrl.isNotBlank()) {
                Text(
                    text = extension.repoUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("extension-details-repo"),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    }
}

@Composable
private fun InfoText(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    primaryTag: String? = null,
    primaryTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(if (primaryTag != null) Modifier.testTag(primaryTag) else Modifier)
            .then(clickableModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = primaryTextStyle,
        )

        Text(
            text = secondaryText + if (onClick != null) " ⓘ" else "",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoDivider() {
    VerticalDivider(
        modifier = Modifier.height(20.dp),
    )
}

@Composable
private fun SourceSwitchPreference(
    source: SourceState,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (SourceDescriptor) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleEnabled(!source.isEnabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("extension-source-${source.source.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = source.source.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${LocaleHelper.getSourceDisplayName(source.source.lang)} • ID: ${source.source.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Browse button
            TextButton(
                onClick = { onClickSource(source.source) },
                modifier = Modifier.testTag("extension-source-browse-${source.source.id}"),
            ) {
                Text(strings.browseSourceBrowse)
            }

            // Configurable gear button
            if (source.isConfigurable) {
                IconButton(
                    onClick = { onClickSourcePreferences(source.source.id) },
                    modifier = Modifier.testTag("extension-source-preferences-${source.source.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = strings.actionSettings,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Per-source enable switch
            Switch(
                checked = source.isEnabled,
                onCheckedChange = { onToggleEnabled(it) },
                modifier = Modifier.testTag("extension-source-enabled-${source.source.id}"),
            )

            // Per-source incognito toggle
            IconButton(
                onClick = { onToggleIncognito(!source.isIncognito) },
                modifier = Modifier.testTag("extension-source-incognito-${source.source.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = strings.extensionIncognitoMode,
                    tint = if (source.isIncognito) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }
}

private fun loadExtensionIcon(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        val file = File(path)
        if (!file.isFile) return null
        org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }.getOrNull()
}
