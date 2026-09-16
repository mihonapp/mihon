package mihon.desktop.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DesktopNavigator(
    initialDestination: DesktopDestination,
    private val onDestinationChanged: (DesktopDestination) -> Unit,
) {
    private var readerReturnDestination: DesktopRoute = initialDestination
    private var detailsReturnDestination = initialDestination

    var current: DesktopRoute by mutableStateOf(initialDestination)
        private set

    fun navigate(destination: DesktopDestination) {
        if (destination == current) return
        current = destination
        onDestinationChanged(destination)
    }

    fun navigate(destination: DesktopDestination.Reader) {
        if (destination == current) return
        if (current !is DesktopDestination.Reader) readerReturnDestination = current
        current = destination
    }

    fun navigate(destination: DesktopDestination.MangaDetails) {
        if (destination == current) return
        val origin = if (current is DesktopDestination.Reader) readerReturnDestination else current
        (origin as? DesktopDestination)?.let { detailsReturnDestination = it }
        current = destination
    }

    fun back() {
        val currentDestination = current
        if (currentDestination is DesktopDestination.Reader) {
            current = readerReturnDestination
        } else if (currentDestination is DesktopDestination.MangaDetails) {
            current = detailsReturnDestination
        }
    }
}
