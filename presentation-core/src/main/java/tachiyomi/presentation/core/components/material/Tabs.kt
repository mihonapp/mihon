package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.components.Pill

@Composable
fun TabText(text: String, badgeCount: Int? = null) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f

    Layout(
        content = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badgeCount != null) {
                Pill(
                    text = "$badgeCount",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                    fontSize = 10.sp,
                )
            }
        },
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val badge = measurables.getOrNull(1)?.measure(looseConstraints)
        val badgeWidth = badge?.width ?: 0
        // The label gets what the badge leaves, unlike with Modifier.weight even in scrollable rows' unbounded tabs
        val label = measurables[0].measure(looseConstraints.offset(horizontal = -badgeWidth))
        val height = maxOf(label.height, badge?.height ?: 0)
        layout(label.width + badgeWidth, height) {
            label.placeRelative(0, (height - label.height) / 2)
            badge?.placeRelative(label.width, (height - badge.height) / 2)
        }
    }
}
