package eu.kanade.presentation.more.settings.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.tachiyomi.R

object SettingsMainScreen : Screen {
    @Composable
    override fun Content() {
        Content(twoPane = false)
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

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val containerColor = if (twoPane) getPalerSurface() else MaterialTheme.colorScheme.surface
        val topBarState = rememberTopAppBarState()
        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                // https://issuetracker.google.com/issues/249688556
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(surface = containerColor),
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.label_settings),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = backPress::invoke) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.abc_action_bar_up_description),
                                )
                            }
                        },
                        actions = {
                            AppBarActions(
                                listOf(
                                    AppBar.Action(
                                        title = stringResource(R.string.action_search),
                                        icon = Icons.Outlined.Search,
                                        onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                                    ),
                                ),
                            )
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
            containerColor = containerColor,
            content = { contentPadding ->
                val state = rememberLazyListState()
                val indexSelected = if (twoPane) {
                    items.indexOfFirst { it.screen::class == navigator.items.first()::class }
                        .also {
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
                        if (twoPane) {
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
                                onPreferenceClick = { navigator.navigate(item.screen, twoPane) },
                            )
                        }
                    }
                }
            },
        )
    }

    private fun Navigator.navigate(screen: Screen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }
}

private data class Item(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val formatSubtitle: @Composable () -> String = { stringResource(subtitleRes) },
    val icon: ImageVector,
    val screen: Screen,
)

private val items = listOf(
    Item(
        titleRes = R.string.pref_category_general,
        subtitleRes = R.string.pref_general_summary,
        icon = Icons.Outlined.Tune,
        screen = SettingsGeneralScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_appearance,
        subtitleRes = R.string.pref_appearance_summary,
        icon = Icons.Outlined.Palette,
        screen = SettingsAppearanceScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_library,
        subtitleRes = R.string.pref_library_summary,
        icon = Icons.Outlined.CollectionsBookmark,
        screen = SettingsLibraryScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_reader,
        subtitleRes = R.string.pref_reader_summary,
        icon = Icons.Outlined.ChromeReaderMode,
        screen = SettingsReaderScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_downloads,
        subtitleRes = R.string.pref_downloads_summary,
        icon = Icons.Outlined.GetApp,
        screen = SettingsDownloadScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_tracking,
        subtitleRes = R.string.pref_tracking_summary,
        icon = Icons.Outlined.Sync,
        screen = SettingsTrackingScreen(),
    ),
    Item(
        titleRes = R.string.browse,
        subtitleRes = R.string.pref_browse_summary,
        icon = Icons.Outlined.Explore,
        screen = SettingsBrowseScreen(),
    ),
    Item(
        titleRes = R.string.label_backup,
        subtitleRes = R.string.pref_backup_summary,
        icon = Icons.Outlined.SettingsBackupRestore,
        screen = SettingsBackupScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_security,
        subtitleRes = R.string.pref_security_summary,
        icon = Icons.Outlined.Security,
        screen = SettingsSecurityScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_advanced,
        subtitleRes = R.string.pref_advanced_summary,
        icon = Icons.Outlined.Code,
        screen = SettingsAdvancedScreen(),
    ),
    Item(
        titleRes = R.string.pref_category_about,
        subtitleRes = 0,
        formatSubtitle = {
            "${stringResource(R.string.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}"
        },
        icon = Icons.Outlined.Info,
        screen = AboutScreen(),
    ),
)
