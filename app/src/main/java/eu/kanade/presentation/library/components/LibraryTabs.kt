package eu.kanade.presentation.library.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.presentation.core.components.material.TabText

@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getItemCountForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    PrimaryScrollableTabRow(
        selectedTabIndex = currentPageIndex,
        edgePadding = 0.dp,
    ) {
        categories.forEachIndexed { index, category ->
            Tab(
                selected = currentPageIndex == index,
                onClick = { onTabItemClick(index) },
                text = {
                    TabText(
                        text = category.visualName,
                        badgeCount = getItemCountForCategory(category),
                    )
                },
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
