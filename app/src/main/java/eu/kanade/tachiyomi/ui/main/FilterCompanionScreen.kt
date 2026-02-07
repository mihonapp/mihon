package eu.kanade.tachiyomi.ui.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.DisplayPage
import eu.kanade.presentation.library.FilterPage
import eu.kanade.presentation.library.SortPage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.FilterItem
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import mihon.core.dualscreen.DualScreenState
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed class FilterCompanionScreen : Screen {

    data class Library(val categoryId: Long?) : FilterCompanionScreen() {
        @Composable
        override fun Content() {
            val settingsScreenModel = rememberScreenModel { LibrarySettingsScreenModel() }
            val scope = rememberCoroutineScope()
            
            val categories by Injekt.get<GetCategories>().subscribe().collectAsState(emptyList())
            val category = categories.find { it.id == categoryId }

            val tabTitles = persistentListOf(
                stringResource(MR.strings.action_filter),
                stringResource(MR.strings.action_sort),
                stringResource(MR.strings.action_display),
            )
            val pagerState = rememberPagerState { tabTitles.size }

            Scaffold(
                topBar = { scrollBehavior ->
                    AppBar(
                        title = stringResource(MR.strings.action_filter),
                        navigateUp = { DualScreenState.close() },
                        scrollBehavior = scrollBehavior,
                    )
                }
            ) { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues)) {
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        divider = {},
                    ) {
                        tabTitles.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { TabText(text = tab) },
                                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    HorizontalDivider()

                    HorizontalPager(
                        modifier = Modifier.fillMaxSize().animateContentSize(),
                        state = pagerState,
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            when (page) {
                                0 -> FilterPage(screenModel = settingsScreenModel)
                                1 -> SortPage(category = category, screenModel = settingsScreenModel)
                                2 -> DisplayPage(screenModel = settingsScreenModel)
                            }
                        }
                    }
                }
            }
        }
    }

    data class Source(
        val sourceId: Long,
        val filters: FilterList,
        val onReset: () -> Unit,
        val onFilter: () -> Unit,
        val onUpdate: (FilterList) -> Unit,
    ) : FilterCompanionScreen() {
        @Composable
        override fun Content() {
            val updateFilters = { onUpdate(filters) }

            Scaffold(
                topBar = { scrollBehavior ->
                    AppBar(
                        title = stringResource(MR.strings.action_filter),
                        navigateUp = { DualScreenState.close() },
                        scrollBehavior = scrollBehavior,
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    stickyHeader {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                        ) {
                            TextButton(onClick = onReset) {
                                Text(
                                    text = stringResource(MR.strings.action_reset),
                                    style = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(onClick = {
                                onFilter()
                                DualScreenState.close()
                            }) {
                                Text(stringResource(MR.strings.action_filter))
                            }
                        }
                        HorizontalDivider()
                    }

                    items(filters) {
                        FilterItem(it, updateFilters)
                    }
                }
            }
        }
    }
}
