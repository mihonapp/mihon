package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.presentation.components.SwipeRefresh
import eu.kanade.presentation.components.rememberPagerState
import eu.kanade.presentation.library.LibraryState
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    state: LibraryState,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    isLibraryEmpty: Boolean,
    showPageTabs: Boolean,
    showMangaCount: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onMangaClicked: (Long) -> Unit,
    onContinueReadingClicked: (LibraryManga) -> Unit,
    onToggleSelection: (LibraryManga) -> Unit,
    onToggleRangeSelection: (LibraryManga) -> Unit,
    onRefresh: (Category?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfMangaForCategory: @Composable (Long) -> State<Int?>,
    getDisplayModeForPage: @Composable (Int) -> LibraryDisplayMode,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getLibraryForPage: @Composable (Int) -> List<LibraryItem>,
    showDownloadBadges: Boolean,
    showUnreadBadges: Boolean,
    showLocalBadges: Boolean,
    showLanguageBadges: Boolean,
    showContinueReadingButton: Boolean,
    isDownloadOnly: Boolean,
    isIncognitoMode: Boolean,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val categories = state.categories
        val coercedCurrentPage = remember { currentPage().coerceAtMost(categories.lastIndex) }
        val pagerState = rememberPagerState(coercedCurrentPage)

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (isLibraryEmpty.not() && showPageTabs && categories.size > 1) {
            LibraryTabs(
                categories = categories,
                currentPageIndex = pagerState.currentPage,
                showMangaCount = showMangaCount,
                getNumberOfMangaForCategory = getNumberOfMangaForCategory,
                isDownloadOnly = isDownloadOnly,
                isIncognitoMode = isIncognitoMode,
                onTabItemClick = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        }

        val onClickManga = { manga: LibraryManga ->
            if (state.selectionMode.not()) {
                onMangaClicked(manga.manga.id)
            } else {
                onToggleSelection(manga)
            }
        }
        val onLongClickManga = { manga: LibraryManga ->
            onToggleRangeSelection(manga)
        }
        val onClickContinueReading = { manga: LibraryManga ->
            onContinueReadingClicked(manga)
        }

        SwipeRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                val started = onRefresh(categories[currentPage()])
                if (!started) return@SwipeRefresh
                scope.launch {
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = state.selectionMode.not(),
        ) {
            LibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                pageCount = categories.size,
                selectedManga = state.selection,
                getDisplayModeForPage = getDisplayModeForPage,
                getColumnsForOrientation = getColumnsForOrientation,
                getLibraryForPage = getLibraryForPage,
                showDownloadBadges = showDownloadBadges,
                showUnreadBadges = showUnreadBadges,
                showLocalBadges = showLocalBadges,
                showLanguageBadges = showLanguageBadges,
                showContinueReadingButton = showContinueReadingButton,
                onClickManga = onClickManga,
                onLongClickManga = onLongClickManga,
                onClickContinueReading = onClickContinueReading,
                onGlobalSearchClicked = onGlobalSearchClicked,
                searchQuery = state.searchQuery,
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
