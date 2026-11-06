package mihon.feature.mekuri.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import mihon.app.di.appGraph
import mihon.feature.mekuri.vault.VaultScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ChromeReaderMode
import mihon.icons.materialsymbols.rounded.Code
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Lock
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.Security
import mihon.icons.materialsymbols.rounded.Storage
import mihon.icons.materialsymbols.rounded.Sync
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.collectAsState

/**
 * Mekuri: the settings home on phones. Search first, the switches people flip most, then clear groups as tiles.
 * Each tile opens Mihon's existing settings page, so every option is still there.
 */
@Composable
fun MekuriSettingsHome(
    onOpen: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val graph = context.appGraph

    val incognitoPref = graph.basePreferences.incognitoMode
    val incognito by incognitoPref.collectAsState()
    val themeModePref = graph.uiPreferences.themeMode
    val themeMode by themeModePref.collectAsState()
    val amoledPref = graph.uiPreferences.themeDarkAmoled
    val amoled by amoledPref.collectAsState()
    val wifiPref = graph.downloadPreferences.downloadOnlyOverWifi
    val wifiOnly by wifiPref.collectAsState()

    val groups = listOf(
        Group("Look", "Theme, dark mode, covers", MaterialSymbols.Rounded.Palette, SettingsAppearanceScreen),
        Group(
            "Reading",
            "Long strip, auto-scroll, taps",
            MaterialSymbols.AutoMirroredRounded.ChromeReaderMode,
            SettingsReaderScreen,
        ),
        Group(
            "Library",
            "Shelves, updates, classic",
            MaterialSymbols.Rounded.CollectionsBookmark,
            SettingsLibraryScreen,
        ),
        Group("Downloads", "Wi-Fi, storage, auto-delete", MaterialSymbols.Rounded.Download, SettingsDownloadScreen),
        Group("Sources", "Repos, languages, NSFW", MaterialSymbols.Rounded.Explore, SettingsBrowseScreen),
        Group("Privacy", "App lock, secure screen", MaterialSymbols.Rounded.Security, SettingsSecurityScreen),
        Group("Trackers", "AniList, MAL and more", MaterialSymbols.Rounded.Sync, SettingsTrackingScreen),
        Group("Backup", "Backup, restore, storage", MaterialSymbols.Rounded.Storage, SettingsDataScreen),
    )

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(title = "Settings", navigateUp = onBack, scrollBehavior = scrollBehavior)
        },
    ) { contentPadding ->
        ScrollbarLazyColumn(contentPadding = contentPadding) {
            item(key = "search") {
                Surface(
                    onClick = { onOpen(SettingsSearchScreen()) },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Search all settings",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            item(key = "quick") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Label("Quick switches")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        QuickChip("Incognito", incognito) { incognitoPref.set(!incognito) }
                        val dark = themeMode == ThemeMode.DARK
                        QuickChip("Dark mode", dark) {
                            val mode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK
                            themeModePref.set(mode)
                            setAppCompatDelegateThemeMode(mode)
                        }
                        QuickChip("Pure black", amoled) {
                            amoledPref.set(!amoled)
                            (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        }
                        QuickChip("Wi-Fi-only downloads", wifiOnly) { wifiPref.set(!wifiOnly) }
                    }
                }
            }

            item(key = "groups-label") {
                Label("Everything else", Modifier.padding(start = 16.dp, top = 8.dp))
            }
            groups.chunked(2).forEachIndexed { index, row ->
                item(key = "groups$index") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { group ->
                            GroupTile(group, Modifier.weight(1f).fillMaxHeight()) { onOpen(group.screen) }
                        }
                    }
                }
            }

            item(key = "more") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SmallRow("Vault", MaterialSymbols.Rounded.Lock) { onOpen(VaultScreen()) }
                    SmallRow("Advanced", MaterialSymbols.Rounded.Code) { onOpen(SettingsAdvancedScreen) }
                    SmallRow("About", MaterialSymbols.Rounded.Info) { onOpen(AboutScreen) }
                }
            }
        }
    }
}

private class Group(val title: String, val hint: String, val icon: ImageVector, val screen: Screen)

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun GroupTile(group: Group, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.heightIn(min = 112.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = group.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = group.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = group.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmallRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
