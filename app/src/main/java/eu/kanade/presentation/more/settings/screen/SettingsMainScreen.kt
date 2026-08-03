package eu.kanade.presentation.more.settings.screen

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.PreferenceScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

object SettingsMainScreen : Screen() {

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

        // E-Ink pagination state for settings categories
        val settingsPagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { items.size }
        )

        // Focus requester for volume key capture
        val pagerFocusRequester = remember { FocusRequester() }
        
        // Debounce state for volume keys (matches PagerViewer logic)
        val lastVolumePressState = remember { mutableLongStateOf(0L) }
        val debounceMs = 600L // 600ms debounce to prevent double-presses on e-ink
        
        val scope = rememberCoroutineScope()

        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_settings),
                    navigateUp = backPress::invoke,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_search),
                                    icon = Icons.Outlined.Search,
                                    onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = containerColor,
            content = { contentPadding ->
                // Focus for volume key capture
                LaunchedEffect(Unit) {
                    pagerFocusRequester.requestFocus()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(pagerFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            val now = System.currentTimeMillis()
                            if (now - lastVolumePressState.longValue < debounceMs) {
                                // Too soon, consume but don't action
                                return@onPreviewKeyEvent true
                            }
                            
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                    lastVolumePressState.longValue = now
                                    // Volume down - next page
                                    scope.launch {
                                        if (settingsPagerState.currentPage < settingsPagerState.pageCount - 1) {
                                            settingsPagerState.scrollToPage(settingsPagerState.currentPage + 1)
                                        } else {
                                            settingsPagerState.scrollToPage(0) // wraparound
                                        }
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_VOLUME_UP -> {
                                    lastVolumePressState.longValue = now
                                    // Volume up - previous page
                                    scope.launch {
                                        if (settingsPagerState.currentPage > 0) {
                                            settingsPagerState.scrollToPage(settingsPagerState.currentPage - 1)
                                        } else {
                                            settingsPagerState.scrollToPage(settingsPagerState.pageCount - 1) // wraparound
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    // HorizontalPager for settings categories - e-ink friendly (no animations)
                    // Each page now shows ALL settings for that category inline
                    HorizontalPager(
                        modifier = Modifier.weight(1f),
                        state = settingsPagerState,
                        verticalAlignment = Alignment.Top,
                        beyondViewportPageCount = 1,
                    ) { page ->
                        val item = items[page]
                        val selected = if (twoPane) {
                            items.indexOfFirst { it.screen::class == navigator.items.first()::class } == page
                        } else {
                            false
                        }
                        
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
                            // Render category header + all settings inline
                            Column(
                                modifier = modifier.padding(contentPadding),
                            ) {
                                // Category header
                                Text(
                                    text = stringResource(item.titleRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                
                                // Render all settings for this category inline
                                // Only screens implementing SearchableSettings have getPreferences()
                                val preferences = when (item.screen) {
                                    SettingsAppearanceScreen -> SettingsAppearanceScreen.getPreferences()
                                    SettingsLibraryScreen -> SettingsLibraryScreen.getPreferences()
                                    SettingsReaderScreen -> SettingsReaderScreen.getPreferences()
                                    SettingsDownloadScreen -> SettingsDownloadScreen.getPreferences()
                                    SettingsTrackingScreen -> SettingsTrackingScreen.getPreferences()
                                    SettingsBrowseScreen -> SettingsBrowseScreen.getPreferences()
                                    SettingsDataScreen -> SettingsDataScreen.getPreferences()
                                    SettingsSecurityScreen -> SettingsSecurityScreen.getPreferences()
                                    SettingsAdvancedScreen -> SettingsAdvancedScreen.getPreferences()
                                    else -> emptyList()
                                }
                                
                                PreferenceScreen(
                                    items = preferences,
                                    contentPadding = PaddingValues(0.dp),
                                )
                            }
                        }
                    }

                    // Page indicators at the bottom (dots + numbers)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Dot indicators
                        repeat(settingsPagerState.pageCount) { page ->
                            Box(
                                modifier = Modifier
                                    .size(if (page == settingsPagerState.currentPage) 12.dp else 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (page == settingsPagerState.currentPage) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        }
                                    ),
                            )
                            if (page < settingsPagerState.pageCount - 1) {
                                Box(modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                        
                        // Spacer between dots and numbers
                        Box(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        // Page number indicator
                        Text(
                            text = "${settingsPagerState.currentPage + 1} / ${settingsPagerState.pageCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            },
        )
    }

    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }

    private data class Item(
        val titleRes: StringResource,
        val subtitleRes: StringResource? = null,
        val formatSubtitle: @Composable () -> String? = { subtitleRes?.let { stringResource(it) } },
        val icon: ImageVector,
        val screen: VoyagerScreen,
    )

    private val items = listOf(
        Item(
            titleRes = MR.strings.pref_category_appearance,
            subtitleRes = MR.strings.pref_appearance_summary,
            icon = Icons.Outlined.Palette,
            screen = SettingsAppearanceScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_library,
            subtitleRes = MR.strings.pref_library_summary,
            icon = Icons.Outlined.CollectionsBookmark,
            screen = SettingsLibraryScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_reader,
            subtitleRes = MR.strings.pref_reader_summary,
            icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
            screen = SettingsReaderScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_downloads,
            subtitleRes = MR.strings.pref_downloads_summary,
            icon = Icons.Outlined.GetApp,
            screen = SettingsDownloadScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_tracking,
            subtitleRes = MR.strings.pref_tracking_summary,
            icon = Icons.Outlined.Sync,
            screen = SettingsTrackingScreen,
        ),
        Item(
            titleRes = MR.strings.browse,
            subtitleRes = MR.strings.pref_browse_summary,
            icon = Icons.Outlined.Explore,
            screen = SettingsBrowseScreen,
        ),
        Item(
            titleRes = MR.strings.label_data_storage,
            subtitleRes = MR.strings.pref_backup_summary,
            icon = Icons.Outlined.Storage,
            screen = SettingsDataScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_security,
            subtitleRes = MR.strings.pref_security_summary,
            icon = Icons.Outlined.Security,
            screen = SettingsSecurityScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_advanced,
            subtitleRes = MR.strings.pref_advanced_summary,
            icon = Icons.Outlined.Code,
            screen = SettingsAdvancedScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_about,
            formatSubtitle = {
                "${stringResource(MR.strings.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}"
            },
            icon = Icons.Outlined.Info,
            screen = AboutScreen,
        ),
    )
}
