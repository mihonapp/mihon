package eu.kanade.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
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
    Icon(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(16.dp),
        painter = painter,
        tint = MaterialTheme.colorScheme.primary,
        contentDescription = label,
    )
}
