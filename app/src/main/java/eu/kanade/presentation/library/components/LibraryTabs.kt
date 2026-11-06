package eu.kanade.presentation.library.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.NeonTabRow
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
    NeonTabRow(
        selectedTabIndex = currentPageIndex,
        tabCount = categories.size,
        onTabClick = onTabItemClick,
        scrollable = true,
    ) { index, _ ->
        val category = categories[index]
        TabText(
            text = category.visualName,
            badgeCount = getItemCountForCategory(category),
        )
    }
}
