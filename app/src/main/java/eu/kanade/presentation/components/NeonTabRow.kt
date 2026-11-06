package eu.kanade.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.neonOutline

/**
 * A pill of tabs where the selected one sits on a sliding accent gradient, the Neon take on a tab row.
 *
 * Tabs share the width equally, or keep their own width and scroll when [scrollable], which then brings the
 * selected tab into view. A [selectedTabIndex] outside the tabs selects none.
 */
@Composable
fun NeonTabRow(
    selectedTabIndex: Int,
    tabCount: Int,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    tabContent: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    // Left edge and width of each tab, in pixels, as laid out.
    val tabBounds = remember(tabCount) {
        mutableStateListOf<Pair<Float, Float>?>().apply { repeat(tabCount) { add(null) } }
    }
    val target = tabBounds.getOrNull(selectedTabIndex)
    val indicatorSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val indicatorLeft by animateFloatAsState(target?.first ?: 0f, indicatorSpring, label = "indicatorLeft")
    val indicatorWidth by animateFloatAsState(target?.second ?: 0f, indicatorSpring, label = "indicatorWidth")
    val gradient = NeonDefaults.accentGradient
    val scrollState = rememberScrollState()
    var viewportWidth by remember { mutableIntStateOf(0) }

    if (scrollable && target != null) {
        LaunchedEffect(selectedTabIndex, target, viewportWidth) {
            val centered = target.first - (viewportWidth - target.second) / 2f
            scrollState.animateScrollTo(centered.toInt().coerceIn(0, scrollState.maxValue))
        }
    }

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(NeonDefaults.PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .neonOutline(NeonDefaults.PillShape, NeonDefaults.outlineColor)
            .onSizeChanged { viewportWidth = it.width },
    ) {
        Row(
            modifier = Modifier
                .padding(3.dp)
                .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth())
                .fillMaxHeight()
                .drawBehind {
                    if (target != null && indicatorWidth > 0f) {
                        drawRoundRect(
                            brush = gradient,
                            topLeft = Offset(indicatorLeft, 0f),
                            size = Size(indicatorWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                }
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(tabCount) { index ->
                val selected = index == selectedTabIndex
                Box(
                    modifier = Modifier
                        .then(if (scrollable) Modifier else Modifier.weight(1f))
                        .fillMaxHeight()
                        .onPlaced { tabBounds[index] = it.positionInParent().x to it.size.width.toFloat() }
                        .clip(NeonDefaults.PillShape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTabClick(index) })
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        LocalTextStyle provides MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                    ) {
                        tabContent(index, selected)
                    }
                }
            }
        }
    }
}
