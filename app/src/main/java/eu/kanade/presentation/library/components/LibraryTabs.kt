package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.TabIndicator
import eu.kanade.presentation.components.TabText

@Composable
fun LibraryTabs(
    categories: List<Category>,
    currentPageIndex: Int,
    showMangaCount: Boolean,
    isDownloadOnly: Boolean,
    isIncognitoMode: Boolean,
    getNumberOfMangaForCategory: @Composable (Long) -> State<Int?>,
    onTabItemClick: (Int) -> Unit,
) {
    Column {
        ScrollableTabRow(
            selectedTabIndex = currentPageIndex,
            edgePadding = 0.dp,
            indicator = { TabIndicator(it[currentPageIndex]) },
            // TODO: use default when width is fixed upstream
            // https://issuetracker.google.com/issues/242879624
            divider = {},
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = currentPageIndex == index,
                    onClick = { onTabItemClick(index) },
                    text = {
                        TabText(
                            text = category.visualName,
                            badgeCount = if (showMangaCount) {
                                getNumberOfMangaForCategory(category.id)
                            } else {
                                null
                            }?.value,
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Divider()

        AppStateBanners(isDownloadOnly, isIncognitoMode)
    }
}
