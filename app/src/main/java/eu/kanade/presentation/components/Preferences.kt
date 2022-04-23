package eu.kanade.presentation.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.horizontalPadding

@Composable
fun PreferenceRow(
    title: String,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val height = if (subtitle != null) 72.dp else 56.dp

    // TODO: adjust text styles, especially subtitles
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        color = contentColorFor(MaterialTheme.colorScheme.background),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(height)
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null
            )
        }
        Column(
            Modifier
                .padding(horizontal = horizontalPadding)
                .weight(1f)
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = textStyle,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = textStyle.copy(
                        fontWeight = FontWeight.Normal,
                    ),
                )
            }
        }
        if (action != null) {
            Box(Modifier.widthIn(min = 56.dp)) {
                action()
            }
        }
    }
}
