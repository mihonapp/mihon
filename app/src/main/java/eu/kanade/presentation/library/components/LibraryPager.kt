package eu.kanade.presentation.library.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.PagerState
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting

@Composable
fun LibraryPager(
    state: PagerState,
    pageCount: Int,
    selectedManga: List<LibraryManga>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getDisplayModeForPage: @Composable (Int) -> State<DisplayModeSetting>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getLibraryForPage: @Composable (Int) -> State<List<LibraryItem>>,
    onClickManga: (LibraryManga) -> Unit,
    onLongClickManga: (LibraryManga) -> Unit,
) {
    HorizontalPager(
        count = pageCount,
        modifier = Modifier.fillMaxSize(),
        state = state,
        verticalAlignment = Alignment.Top,
    ) { page ->
        val library by getLibraryForPage(page)
        val displayMode by getDisplayModeForPage(page)
        val columns by if (displayMode != DisplayModeSetting.LIST) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            remember(isLandscape) { getColumnsForOrientation(isLandscape) }
        } else {
            remember { mutableStateOf(0) }
        }

        when (displayMode) {
            DisplayModeSetting.LIST -> {
                LibraryList(
                    items = library,
                    selection = selectedManga,
                    onClick = onClickManga,
                    onLongClick = onLongClickManga,
                    searchQuery = searchQuery,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                )
            }
            DisplayModeSetting.COMPACT_GRID -> {
                LibraryCompactGrid(
                    items = library,
                    columns = columns,
                    selection = selectedManga,
                    onClick = onClickManga,
                    onLongClick = onLongClickManga,
                    searchQuery = searchQuery,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                )
            }
            DisplayModeSetting.COMFORTABLE_GRID -> {
                LibraryComfortableGrid(
                    items = library,
                    columns = columns,
                    selection = selectedManga,
                    onClick = onClickManga,
                    onLongClick = onLongClickManga,
                    searchQuery = searchQuery,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                )
            }
            DisplayModeSetting.COVER_ONLY_GRID -> {
                LibraryCoverOnlyGrid(
                    items = library,
                    columns = columns,
                    selection = selectedManga,
                    onClick = onClickManga,
                    onLongClick = onLongClickManga,
                    searchQuery = searchQuery,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                )
            }
        }
    }
}
