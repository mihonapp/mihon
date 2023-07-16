package tachiyomi.presentation.core.components.material

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.components.Pill

private fun Modifier.tabIndicatorOffset(
    currentTabPosition: TabPosition,
    currentPageOffsetFraction: Float,
) = fillMaxWidth()
    .wrapContentSize(Alignment.BottomStart)
    .composed {
        val currentTabWidth by animateDpAsState(
            targetValue = currentTabPosition.width,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )
        val offset by animateDpAsState(
            targetValue = currentTabPosition.left + (currentTabWidth * currentPageOffsetFraction),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )
        Modifier
            .offset { IntOffset(x = offset.roundToPx(), y = 0) }
            .width(currentTabWidth)
    }

@Composable
fun TabIndicator(
    currentTabPosition: TabPosition,
    currentPageOffsetFraction: Float,
) {
    SecondaryIndicator(
        modifier = Modifier
            .tabIndicatorOffset(currentTabPosition, currentPageOffsetFraction)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
    )
}

@Composable
fun TabText(
    text: String,
    badgeCount: Int? = null,
) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text)
        if (badgeCount != null) {
            Pill(
                text = "$badgeCount",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                fontSize = 10.sp,
            )
        }
    }
}
