package eu.kanade.tachiyomi.ui.setting

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.LocalSettingsNavIcon
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.TwoPanelStartWidth

// Above this fraction of the available width, the persistent sidebar is shown as an
// expandable overlay drawer instead of taking space side-by-side.
private const val SettingsSidebarOverlayThreshold = 0.4f

class SettingsScreen(
    private val destination: Int? = null,
) : Screen() {

    constructor(destination: Destination) : this(destination.id)

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow
        if (!isTabletUi()) {
            Navigator(
                screen = when (destination) {
                    Destination.About.id -> AboutScreen
                    Destination.DataAndStorage.id -> SettingsDataScreen
                    Destination.Tracking.id -> SettingsTrackingScreen
                    else -> SettingsMainScreen
                },
                onBackPressed = null,
            ) {
                val pop: () -> Unit = {
                    if (it.canPop) {
                        it.pop()
                    } else {
                        parentNavigator.pop()
                    }
                }
                CompositionLocalProvider(LocalBackPress provides pop) {
                    DefaultNavigatorScreenTransition(navigator = it)
                }
            }
        } else {
            Navigator(
                screen = when (destination) {
                    Destination.About.id -> AboutScreen
                    Destination.DataAndStorage.id -> SettingsDataScreen
                    Destination.Tracking.id -> SettingsTrackingScreen
                    else -> SettingsAppearanceScreen
                },
                onBackPressed = null,
            ) { navigator ->
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val sidebarWidth = (maxWidth / 2).coerceAtMost(TwoPanelStartWidth)
                    if (sidebarWidth > maxWidth * SettingsSidebarOverlayThreshold) {
                        // Narrow layouts: show the sidebar as an expandable overlay drawer.
                        val drawerState = rememberDrawerState(DrawerValue.Open)
                        val scope = rememberCoroutineScope()
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(drawerState = drawerState) {
                                    CompositionLocalProvider(LocalBackPress provides parentNavigator::pop) {
                                        SettingsMainScreen.Content(
                                            twoPane = true,
                                            onItemClick = { scope.launch { drawerState.close() } },
                                        )
                                    }
                                }
                            },
                        ) {
                            CompositionLocalProvider(
                                LocalBackPress provides { scope.launch { drawerState.open() } },
                                LocalSettingsNavIcon provides Icons.Outlined.Menu,
                            ) {
                                DefaultNavigatorScreenTransition(navigator = navigator)
                            }
                        }
                    } else {
                        // Wide layouts: keep the sidebar persistently side-by-side.
                        val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                        TwoPanelBox(
                            modifier = Modifier
                                .windowInsetsPadding(insets)
                                .consumeWindowInsets(insets),
                            startContent = {
                                CompositionLocalProvider(LocalBackPress provides parentNavigator::pop) {
                                    SettingsMainScreen.Content(twoPane = true)
                                }
                            },
                            endContent = { DefaultNavigatorScreenTransition(navigator = navigator) },
                        )
                    }
                }
            }
        }
    }

    sealed class Destination(val id: Int) {
        data object About : Destination(0)
        data object DataAndStorage : Destination(1)
        data object Tracking : Destination(2)
    }
}
