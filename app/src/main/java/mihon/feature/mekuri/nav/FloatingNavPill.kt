package mihon.feature.mekuri.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space the pill and its margins take at the bottom of the screen, above the system navigation bar. */
val FloatingNavPillHeight: Dp = 64.dp
val FloatingNavPillMargin: Dp = 16.dp

class FloatingNavItem(
    val key: Any,
    val title: String,
    val icon: Painter,
    val selected: Boolean,
    val badge: Int,
    val onClick: () -> Unit,
)

/**
 * Mekuri: the floating, icons-only navigation pill used on phones instead of Mihon's full-width bottom bar.
 */
@Composable
fun FloatingNavPill(
    items: List<FloatingNavItem>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(FloatingNavPillHeight / 2),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item -> PillItem(item) }
        }
    }
}

@Composable
private fun PillItem(item: FloatingNavItem) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (item.selected) scheme.secondaryContainer else Color.Transparent,
        label = "pillItemBackground",
    )
    val tint by animateColorAsState(
        if (item.selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        label = "pillItemTint",
    )
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
            .semantics { contentDescription = item.title },
        contentAlignment = Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (item.badge > 0) {
                    Badge(containerColor = scheme.secondary, contentColor = scheme.onSecondary) {
                        Text(item.badge.toString())
                    }
                }
            },
        ) {
            Icon(painter = item.icon, contentDescription = null, tint = tint)
        }
    }
}
