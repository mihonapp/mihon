package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun LinkIcon(modifier: Modifier = Modifier, label: String, icon: ImageVector, url: String) {
    val uriHandler = LocalUriHandler.current
    IconButton(
        modifier = modifier.padding(4.dp),
        onClick = { uriHandler.openUri(url) },
    ) {
        Icon(
            imageVector = icon,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = label,
        )
    }
}
