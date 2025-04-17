package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

class ManageDuplicatesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val tabs = persistentListOf(
            possibleDuplicatesTab(),
            hiddenDuplicatesTab(),
        )

        val state = rememberPagerState { tabs.size }

        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_manage_duplicates),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = { AppBarActions(tabs[state.currentPage].actions) },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            Column(
                modifier = Modifier.padding(
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
            ) {
                PrimaryTabRow(
                    selectedTabIndex = state.currentPage,
                    modifier = Modifier.zIndex(1f),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = state.currentPage == index,
                            onClick = { scope.launch { state.animateScrollToPage(index) } },
                            text = { TabText(text = stringResource(tab.titleRes), badgeCount = tab.badgeNumber) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    verticalAlignment = Alignment.Top,
                    userScrollEnabled = false,
                ) { page ->
                    tabs[page].content(
                        PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                        snackbarHostState,
                    )
                }
            }
        }
    }
}
