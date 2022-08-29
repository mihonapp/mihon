package eu.kanade.presentation.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.TabIndicator
import eu.kanade.presentation.components.TabText
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(
    startIndex: Int? = null,
    tabs: List<BrowseTab>,
) {
    val scope = rememberCoroutineScope()
    val state = rememberPagerState()

    LaunchedEffect(startIndex) {
        if (startIndex != null) {
            state.scrollToPage(startIndex)
        }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            AppBar(
                title = stringResource(R.string.browse),
                actions = {
                    AppBarActions(tabs[state.currentPage].actions)
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
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

            HorizontalPager(
                count = tabs.size,
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content()
            }
        }
    }
}

data class BrowseTab(
    @StringRes val titleRes: Int,
    val badgeNumber: Int? = null,
    val actions: List<AppBar.Action> = emptyList(),
    val content: @Composable () -> Unit,
)
