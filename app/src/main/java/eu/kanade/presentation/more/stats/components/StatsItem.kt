package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import tachiyomi.presentation.core.components.material.SecondaryItemAlpha
import tachiyomi.presentation.core.components.material.padding

@Composable
fun RowScope.StatsOverviewItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    BaseStatsItem(
        title = title,
        titleStyle = MaterialTheme.typography.titleLarge,
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.bodyMedium,
        icon = icon,
    )
}

@Composable
fun RowScope.StatsItem(
    title: String,
    subtitle: String,
) {
    BaseStatsItem(
        title = title,
        titleStyle = MaterialTheme.typography.bodyMedium,
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun RowScope.BaseStatsItem(
    title: String,
    titleStyle: TextStyle,
    subtitle: String,
    subtitleStyle: TextStyle,
    icon: ImageVector? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = titleStyle
                .copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style = subtitleStyle
                .copy(
                    color = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = SecondaryItemAlpha),
                ),
            textAlign = TextAlign.Center,
        )
        if (icon != null) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
