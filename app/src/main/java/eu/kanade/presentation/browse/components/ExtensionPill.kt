package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.material.padding

@Composable
internal fun ExtensionPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        modifier = modifier
            .background(color = color.copy(alpha = PILL_BACKGROUND_ALPHA), shape = MaterialTheme.shapes.small)
            .padding(horizontal = MaterialTheme.padding.small, vertical = 2.dp),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
    )
}

private const val PILL_BACKGROUND_ALPHA = 0.12f
