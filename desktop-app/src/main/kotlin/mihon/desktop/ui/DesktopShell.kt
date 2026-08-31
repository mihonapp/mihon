package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.desktop.navigation.DesktopDestination

@Composable
fun DesktopShell(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    val primary = DesktopDestination.entries.take(5)
    val secondary = DesktopDestination.entries.drop(5)
    Surface(modifier = Modifier.fillMaxSize()) {
        Row {
            NavigationRail(modifier = Modifier.fillMaxHeight().width(80.dp)) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        primary.forEach { destination ->
                            DestinationItem(destination, selected, onDestinationSelected)
                        }
                    }
                    Column {
                        HorizontalDivider()
                        secondary.forEach { destination ->
                            DestinationItem(destination, selected, onDestinationSelected)
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(text = selected.label, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun DestinationItem(
    destination: DesktopDestination,
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    NavigationRailItem(
        selected = destination == selected,
        onClick = { onDestinationSelected(destination) },
        icon = { Text(destination.shortLabel) },
        label = { Text(destination.label) },
        alwaysShowLabel = false,
    )
}
