package mihon.feature.mekuri.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.more.MoreViewModel
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.Book
import mihon.icons.materialsymbols.rounded.CloudOff
import mihon.icons.materialsymbols.rounded.Eyeglasses2
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.QueryStats
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.Storage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Mekuri: the "You" hub, replacing Mihon's More tab. History, settings, stats, backup, categories and the
 * incognito / downloaded-only switches, laid out as tiles instead of a long list. (The vault joins in v1.4.)
 */
data object YouTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 4u,
            title = stringResource(MR.strings.mekuri_tab_you),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Person),
        )

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(SettingsScreen())
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<MoreViewModel>()

        val tiles = listOf(
            Tile(stringResource(MR.strings.history), MaterialSymbols.Rounded.Book) { navigator.push(HistoryTab) },
            Tile(stringResource(MR.strings.label_settings), MaterialSymbols.Rounded.Settings) {
                navigator.push(SettingsScreen())
            },
            Tile(stringResource(MR.strings.label_stats), MaterialSymbols.Rounded.QueryStats) {
                navigator.push(StatsScreen())
            },
            Tile(stringResource(MR.strings.mekuri_you_backup), MaterialSymbols.Rounded.Storage) {
                navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage))
            },
            Tile(stringResource(MR.strings.categories), MaterialSymbols.AutoMirroredRounded.Label) {
                navigator.push(CategoryScreen())
            },
            Tile(stringResource(MR.strings.pref_category_about), MaterialSymbols.Rounded.Info) {
                navigator.push(SettingsScreen(SettingsScreen.Destination.About))
            },
        )

        Scaffold { contentPadding ->
            ScrollbarLazyColumn(contentPadding = contentPadding) {
                item(key = "title") {
                    Text(
                        text = stringResource(MR.strings.mekuri_tab_you),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                    )
                }
                tiles.chunked(2).forEachIndexed { index, row ->
                    item(key = "tiles$index") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { tile -> TileCard(tile, Modifier.weight(1f)) }
                        }
                    }
                }
                item(key = "switches") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column {
                            SwitchPreferenceWidget(
                                title = stringResource(MR.strings.pref_incognito_mode),
                                subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                                icon = MaterialSymbols.Rounded.Eyeglasses2,
                                checked = viewModel.incognitoMode,
                                onCheckedChanged = { viewModel.incognitoMode = it },
                            )
                            SwitchPreferenceWidget(
                                title = stringResource(MR.strings.label_downloaded_only),
                                subtitle = stringResource(MR.strings.downloaded_only_summary),
                                icon = MaterialSymbols.Rounded.CloudOff,
                                checked = viewModel.downloadedOnly,
                                onCheckedChanged = { viewModel.downloadedOnly = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

private class Tile(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun TileCard(tile: Tile, modifier: Modifier = Modifier) {
    Surface(
        onClick = tile.onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.heightIn(min = 104.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = tile.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
