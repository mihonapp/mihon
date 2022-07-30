package eu.kanade.presentation.library.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.pager.PagerState
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.components.DownloadedOnlyModeBanner
import eu.kanade.presentation.components.IncognitoModeBanner
import eu.kanade.presentation.components.Pill
import kotlinx.coroutines.launch

@Composable
fun LibraryTabs(
    state: PagerState,
    categories: List<Category>,
    showMangaCount: Boolean,
    isDownloadOnly: Boolean,
    isIncognitoMode: Boolean,
    getNumberOfMangaForCategory: @Composable (Long) -> State<Int?>,
) {
    val scope = rememberCoroutineScope()

    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f

    Column {
        ScrollableTabRow(
            selectedTabIndex = state.currentPage,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier
                        .tabIndicatorOffset(tabPositions[state.currentPage])
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            },
        ) {
            categories.forEachIndexed { index, category ->
                val count by if (showMangaCount) {
                    getNumberOfMangaForCategory(category.id)
                } else {
                    remember { mutableStateOf<Int?>(null) }
                }
                Tab(
                    selected = state.currentPage == index,
                    onClick = { scope.launch { state.animateScrollToPage(index) } },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                color = if (state.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            )
                            if (count != null) {
                                Pill(
                                    text = "$count",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    },
                )
            }
        }
        if (isDownloadOnly) {
            DownloadedOnlyModeBanner()
        }
        if (isIncognitoMode) {
            IncognitoModeBanner()
        }
    }
}
