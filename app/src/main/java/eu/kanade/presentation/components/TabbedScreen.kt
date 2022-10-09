package eu.kanade.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView
import kotlinx.coroutines.launch

@Composable
fun TabbedScreen(
    @StringRes titleRes: Int,
    tabs: List<TabContent>,
    startIndex: Int? = null,
    searchQuery: String? = null,
    @StringRes placeholderRes: Int? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
) {
    val scope = rememberCoroutineScope()
    val state = rememberPagerState()

    LaunchedEffect(startIndex) {
        if (startIndex != null) {
            state.scrollToPage(startIndex)
        }
    }

    Scaffold(
        topBar = {
            if (searchQuery == null) {
                AppBar(
                    title = stringResource(titleRes),
                    actions = {
                        AppBarActions(tabs[state.currentPage].actions)
                    },
                )
            } else {
                SearchToolbar(
                    searchQuery = searchQuery,
                    placeholderText = placeholderRes?.let { stringResource(it) },
                    onChangeSearchQuery = {
                        onChangeSearchQuery(it)
                    },
                    onClickCloseSearch = {
                        onChangeSearchQuery(null)
                    },
                    onClickResetSearch = {
                        onChangeSearchQuery("")
                    },
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
        ) {
            TabRow(
                selectedTabIndex = state.currentPage,
                indicator = { TabIndicator(it[state.currentPage]) },
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = state.currentPage == index,
                        onClick = { scope.launch { state.animateScrollToPage(index) } },
                        text = {
                            TabText(stringResource(tab.titleRes), tab.badgeNumber, state.currentPage == index)
                        },
                    )
                }
            }

            AppStateBanners(downloadedOnlyMode, incognitoMode)

            HorizontalPager(
                count = tabs.size,
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content(
                    TachiyomiBottomNavigationView.withBottomNavPadding(
                        PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    ),
                )
            }
        }
    }
}

data class TabContent(
    @StringRes val titleRes: Int,
    val badgeNumber: Int? = null,
    val actions: List<AppBar.Action> = emptyList(),
    val content: @Composable (contentPadding: PaddingValues) -> Unit,
)
