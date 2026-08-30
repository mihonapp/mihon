package mihon.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import mihon.desktop.DesktopRuntime

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    Window(onCloseRequest = ::exitApplication, title = "Mihon W") {
        Text("Mihon W data: ${runtime.directories.root}")
    }
}
