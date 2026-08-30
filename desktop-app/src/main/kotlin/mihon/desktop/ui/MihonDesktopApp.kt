package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import mihon.desktop.DesktopRuntime
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import java.awt.Frame
import java.awt.Toolkit
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    var preferences by remember { mutableStateOf(runtime.preferences.load()) }
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    val screen = remember { ScreenBounds(0, 0, screenSize.width, screenSize.height) }
    val savedPlacement = remember(preferences.windowPlacement) {
        (preferences.windowPlacement ?: WindowPlacement(0, 0, 1280, 800, false)).sanitize(screen)
    }
    val navigator = remember {
        DesktopNavigator(preferences.lastDestination) { destination ->
            preferences = preferences.copy(lastDestination = destination)
            runtime.preferences.save(preferences)
        }
    }
    val windowState = rememberWindowState(
        placement = if (savedPlacement.maximized) {
            ComposeWindowPlacement.Maximized
        } else {
            ComposeWindowPlacement.Floating
        },
        position = WindowPosition(savedPlacement.x.dp, savedPlacement.y.dp),
        width = savedPlacement.width.dp,
        height = savedPlacement.height.dp,
    )
    var composeWindow: ComposeWindow? by remember { mutableStateOf(null) }

    Window(
        onCloseRequest = {
            composeWindow?.let { window ->
                preferences = preferences.copy(
                    windowPlacement = WindowPlacement(
                        x = window.x,
                        y = window.y,
                        width = window.width,
                        height = window.height,
                        maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
                    ).sanitize(screen),
                )
                runtime.preferences.save(preferences)
            }
            exitApplication()
        },
        state = windowState,
        title = "Mihon W",
    ) {
        SideEffect { composeWindow = window }
        MihonDesktopTheme(preferences.themeMode) {
            DesktopShell(
                selected = navigator.current,
                onDestinationSelected = navigator::navigate,
            )
        }
    }
}
