package eu.kanade.presentation.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category

@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getItemCountForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEachIndexed { index, category ->
            val selected = currentPageIndex == index
            val accent = when (index % 3) {
                0 -> Color(0xFF00E676)
                1 -> Color(0xFFB388FF)
                else -> Color(0xFFFF6E40)
            }
            FilterChip(
                selected = selected,
                onClick = { onTabItemClick(index) },
                label = {
                    val count = getItemCountForCategory(category)
                    val name = if (category.isSystemCategory) "ALL" else category.visualName.uppercase()
                    Text(if (count == null) name else "$name  $count")
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF111726),
                    labelColor = Color(0xFFD4D8E2),
                    selectedContainerColor = accent.copy(alpha = 0.18f),
                    selectedLabelColor = Color.White,
                ),
                border = BorderStroke(if (selected) 1.5.dp else 1.dp, accent.copy(alpha = if (selected) 1f else 0.62f)),
            )
        }
    }
}
