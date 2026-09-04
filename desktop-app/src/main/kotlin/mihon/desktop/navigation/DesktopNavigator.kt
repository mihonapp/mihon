package mihon.desktop.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DesktopNavigator(
    initialDestination: DesktopDestination,
    private val onDestinationChanged: (DesktopDestination) -> Unit,
) {
    var current: DesktopRoute by mutableStateOf(initialDestination)
        private set

    fun navigate(destination: DesktopDestination) {
        if (destination == current) return
        current = destination
        onDestinationChanged(destination)
    }

    fun navigate(destination: DesktopDestination.Reader) {
        if (destination == current) return
        current = destination
    }

    fun back() {
        val currentDestination = current
        if (currentDestination is DesktopDestination.Reader) {
            current = DesktopDestination.Library
        }
    }
}
