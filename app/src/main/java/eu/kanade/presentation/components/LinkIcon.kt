package eu.kanade.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun LinkIcon(
    modifier: Modifier = Modifier,
    label: String,
    painter: Painter,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    LinkIcon(modifier, label, painter) { uriHandler.openUri(url) }
}

@Composable
fun LinkIcon(
    modifier: Modifier = Modifier,
    label: String,
    painter: Painter,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            modifier = modifier.padding(16.dp),
            painter = painter,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = label,
        )
    }
}
