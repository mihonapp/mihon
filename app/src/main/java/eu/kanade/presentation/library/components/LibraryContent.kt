package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.SwipeRefreshIndicator
import eu.kanade.presentation.library.LibraryState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.widget.EmptyView

@Composable
fun LibraryContent(
    state: LibraryState,
    contentPadding: PaddingValues,
    currentPage: Int,
    isLibraryEmpty: Boolean,
    isDownloadOnly: Boolean,
    isIncognitoMode: Boolean,
    showPageTabs: Boolean,
    showMangaCount: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onMangaClicked: (Long) -> Unit,
    onToggleSelection: (LibraryManga) -> Unit,
    onRefresh: (Category?) -> Unit,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfMangaForCategory: @Composable (Long) -> State<Int?>,
    getDisplayModeForPage: @Composable (Int) -> State<DisplayModeSetting>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getLibraryForPage: @Composable (Int) -> State<List<LibraryItem>>,
) {
    val categories = state.categories
    val pagerState = rememberPagerState(currentPage.coerceAtMost(categories.lastIndex))

    Column(
        modifier = Modifier.padding(contentPadding),
    ) {
        if (showPageTabs && categories.size > 1) {
            LibraryTabs(
                state = pagerState,
                categories = categories,
                showMangaCount = showMangaCount,
                getNumberOfMangaForCategory = getNumberOfMangaForCategory,
                isDownloadOnly = isDownloadOnly,
                isIncognitoMode = isIncognitoMode,
            )
        }

        val onClickManga = { manga: LibraryManga ->
            if (state.selectionMode.not()) {
                onMangaClicked(manga.id!!)
            } else {
                onToggleSelection(manga)
            }
        }
        val onLongClickManga = { manga: LibraryManga ->
            onToggleSelection(manga)
        }

        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = false),
            onRefresh = { onRefresh(categories[currentPage]) },
            indicator = { s, trigger ->
                SwipeRefreshIndicator(
                    state = s,
                    refreshTriggerDistance = trigger,
                )
            },
        ) {
            if (state.searchQuery.isNullOrEmpty() && isLibraryEmpty) {
                val handler = LocalUriHandler.current
                EmptyScreen(
                    R.string.information_empty_library,
                    listOf(
                        EmptyView.Action(R.string.getting_started_guide, R.drawable.ic_help_24dp) {
                            handler.openUri("https://tachiyomi.org/help/guides/getting-started")
                        },
                    ),
                )
                return@SwipeRefresh
            }

            LibraryPager(
                state = pagerState,
                pageCount = categories.size,
                selectedManga = state.selection,
                getDisplayModeForPage = getDisplayModeForPage,
                getColumnsForOrientation = getColumnsForOrientation,
                getLibraryForPage = getLibraryForPage,
                onClickManga = onClickManga,
                onLongClickManga = onLongClickManga,
                onGlobalSearchClicked = onGlobalSearchClicked,
                searchQuery = state.searchQuery,
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
