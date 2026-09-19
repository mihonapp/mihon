package eu.kanade.tachiyomi.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedRoute
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceRoute
import eu.kanade.presentation.more.settings.screen.SettingsBrowseRoute
import eu.kanade.presentation.more.settings.screen.SettingsDataRoute
import eu.kanade.presentation.more.settings.screen.SettingsDownloadRoute
import eu.kanade.presentation.more.settings.screen.SettingsLibraryRoute
import eu.kanade.presentation.more.settings.screen.SettingsReaderRoute
import eu.kanade.presentation.more.settings.screen.SettingsSearchRoute
import eu.kanade.presentation.more.settings.screen.SettingsSecurityRoute
import eu.kanade.presentation.more.settings.screen.SettingsTrackingRoute
import eu.kanade.presentation.more.settings.screen.about.AboutRoute
import eu.kanade.presentation.more.settings.screen.about.getVersionName
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.isTabletUi
import kotlinx.serialization.Serializable
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ChromeReaderMode
import mihon.icons.materialsymbols.rounded.Code
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.Security
import mihon.icons.materialsymbols.rounded.Storage
import mihon.icons.materialsymbols.rounded.Sync
import mihon.navigation.util.LocalBackStack
import mihon.navigation.util.replace
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Serializable
data class SettingsRoute(val settingsDestination: SettingsDestination? = null) : NavKey

@Serializable
sealed class SettingsDestination(val id: Int) {
    @Serializable
    data object About : SettingsDestination(0)

    @Serializable
    data object DataAndStorage : SettingsDestination(1)

    @Serializable
    data object Tracking : SettingsDestination(2)
}

fun NavBackStack<NavKey>.addSettingsRoute(settingsRoute: SettingsRoute, isTabletUi: Boolean) {
    val route = when (settingsRoute.settingsDestination) {
        SettingsDestination.About -> AboutRoute
        SettingsDestination.DataAndStorage -> SettingsDataRoute
        SettingsDestination.Tracking -> SettingsTrackingRoute
        null if isTabletUi -> SettingsAppearanceRoute
        null -> null
    }
    if (route != null) {
        if (isTabletUi) {
            add(SettingsRoute())
        }
        add(route)
    } else {
        add(SettingsRoute())
    }
}

@Composable
fun SettingsScreen() {
    val backStack = LocalBackStack.current
    val isTabletUi = isTabletUi()

    val containerColor = if (isTabletUi) getPalerSurface() else MaterialTheme.colorScheme.surface
    val topBarState = rememberTopAppBarState()

    Scaffold(
        topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_settings),
                navigateUp = {
                    if (isTabletUi) backStack.removeLastOrNull()
                    backStack.removeLastOrNull()
                },
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_search),
                                icon = MaterialSymbols.Rounded.Search,
                                onClick = { backStack.navigate(SettingsSearchRoute, isTabletUi) },
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = containerColor,
        content = { contentPadding ->
            val state = rememberLazyListState()
            val indexSelected = if (isTabletUi) {
                items.indexOfFirst { it.route == backStack.last() }
                    .takeIf { it > -1 } // Don't trigger if route for right side hasn't been added yet
                    ?.also {
                        LaunchedEffect(Unit) {
                            state.animateScrollToItem(it)
                            if (it > 0) {
                                // Lift scroll
                                topBarState.contentOffset = topBarState.heightOffsetLimit
                            }
                        }
                    }
            } else {
                null
            }

            LazyColumn(
                state = state,
                contentPadding = contentPadding,
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.hashCode() },
                ) { index, item ->
                    val selected = indexSelected == index
                    var modifier: Modifier = Modifier
                    var contentColor = LocalContentColor.current
                    if (isTabletUi) {
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .then(
                                if (selected) {
                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                } else {
                                    Modifier
                                },
                            )
                        if (selected) {
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        TextPreferenceWidget(
                            modifier = modifier,
                            title = stringResource(item.titleRes),
                            subtitle = item.formatSubtitle(),
                            icon = item.icon,
                            onPreferenceClick = { backStack.navigate(item.route, isTabletUi) },
                        )
                    }
                }
            }
        },
    )
}

fun NavBackStack<NavKey>.navigate(route: NavKey, isTabletUi: Boolean) {
    if (isTabletUi) {
        replace(route)
    } else {
        add(route)
    }
}

@Composable
private fun getPalerSurface(): Color {
    val surface = MaterialTheme.colorScheme.surface
    val dark = isSystemInDarkTheme()
    return remember(surface, dark) {
        val arr = FloatArray(3)
        ColorUtils.colorToHSL(surface.toArgb(), arr)
        arr[2] = if (dark) {
            arr[2] - 0.05f
        } else {
            arr[2] + 0.02f
        }.coerceIn(0f, 1f)
        Color.hsl(arr[0], arr[1], arr[2])
    }
}

private data class Item(
    val titleRes: StringResource,
    val subtitleRes: StringResource? = null,
    val formatSubtitle: @Composable () -> String? = { subtitleRes?.let { stringResource(it) } },
    val icon: ImageVector,
    val route: NavKey,
)

private val items = listOf(
    Item(
        titleRes = MR.strings.pref_category_appearance,
        subtitleRes = MR.strings.pref_appearance_summary,
        icon = MaterialSymbols.Rounded.Palette,
        route = SettingsAppearanceRoute,
    ),
    Item(
        titleRes = MR.strings.pref_category_library,
        subtitleRes = MR.strings.pref_library_summary,
        icon = MaterialSymbols.Rounded.CollectionsBookmark,
        route = SettingsLibraryRoute,
    ),
    Item(
        titleRes = MR.strings.pref_category_reader,
        subtitleRes = MR.strings.pref_reader_summary,
        icon = MaterialSymbols.AutoMirroredRounded.ChromeReaderMode,
        route = SettingsReaderRoute,
    ),
    Item(
        titleRes = MR.strings.pref_category_downloads,
        subtitleRes = MR.strings.pref_downloads_summary,
        icon = MaterialSymbols.Rounded.Download,
        route = SettingsDownloadRoute,
    ),
    Item(
        titleRes = MR.strings.pref_category_tracking,
        subtitleRes = MR.strings.pref_tracking_summary,
        icon = MaterialSymbols.Rounded.Sync,
        route = SettingsTrackingRoute,
    ),
    Item(
        titleRes = MR.strings.browse,
        subtitleRes = MR.strings.pref_browse_summary,
        icon = MaterialSymbols.Rounded.Explore,
        route = SettingsBrowseRoute,
    ),
    Item(
        titleRes = MR.strings.label_data_storage,
        subtitleRes = MR.strings.pref_backup_summary,
        icon = MaterialSymbols.Rounded.Storage,
        route = SettingsDataRoute,
    ),
    Item(
        titleRes = MR.strings.pref_category_security,
        subtitleRes = MR.strings.pref_security_summary,
        icon = MaterialSymbols.Rounded.Security,
        route = SettingsSecurityRoute,
    ),
    Item(
        titleRes = MR.strings.pref_category_advanced,
        subtitleRes = MR.strings.pref_advanced_summary,
        icon = MaterialSymbols.Rounded.Code,
        route = SettingsAdvancedRoute,
    ),
    Item(
        titleRes = MR.strings.pref_category_about,
        formatSubtitle = {
            "${stringResource(MR.strings.app_name)} ${getVersionName(withBuildDate = false)}"
        },
        icon = MaterialSymbols.Rounded.Info,
        route = AboutRoute,
    ),
)
