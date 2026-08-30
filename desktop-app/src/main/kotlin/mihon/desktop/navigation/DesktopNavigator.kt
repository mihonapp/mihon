package mihon.desktop.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DesktopNavigator(
    initialDestination: DesktopDestination,
    private val onDestinationChanged: (DesktopDestination) -> Unit,
) {
    var current: DesktopDestination by mutableStateOf(initialDestination)
        private set

    fun navigate(destination: DesktopDestination) {
        if (destination == current) return
        current = destination
        onDestinationChanged(destination)
    }
}
