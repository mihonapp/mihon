package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.manga.DownloadAction
import kotlin.math.roundToInt

@Composable
fun MangaTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    author: String?,
    artist: String?,
    description: String?,
    tagsProvider: () -> List<String>?,
    coverDataProvider: () -> Manga,
    sourceName: String,
    isStubSource: Boolean,
    favorite: Boolean,
    status: Long,
    trackingCount: Int,
    chapterCount: Int?,
    chapterFiltered: Boolean,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    fromSource: Boolean,
    onBackClicked: () -> Unit,
    onCoverClick: () -> Unit,
    onTagClicked: (String) -> Unit,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onFilterButtonClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    doGlobalSearch: (query: String, global: Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSmallAppBarHeightChanged: (Int) -> Unit,
) {
    val scrollPercentageProvider = { scrollBehavior?.scrollFraction?.coerceIn(0f, 1f) ?: 0f }
    val inverseScrollPercentageProvider = { 1f - scrollPercentageProvider() }

    Layout(
        modifier = modifier,
        content = {
            val (smallHeightPx, onSmallHeightPxChanged) = remember { mutableStateOf(0) }
            Column(modifier = Modifier.layoutId("mangaInfo")) {
                MangaInfoHeader(
                    windowWidthSizeClass = WindowWidthSizeClass.Compact,
                    appBarPadding = with(LocalDensity.current) { smallHeightPx.toDp() },
                    title = title,
                    author = author,
                    artist = artist,
                    description = description,
                    tagsProvider = tagsProvider,
                    sourceName = sourceName,
                    isStubSource = isStubSource,
                    coverDataProvider = coverDataProvider,
                    favorite = favorite,
                    status = status,
                    trackingCount = trackingCount,
                    fromSource = fromSource,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onTagClicked = onTagClicked,
                    onEditCategory = onEditCategoryClicked,
                    onCoverClick = onCoverClick,
                    doSearch = doGlobalSearch,
                )
                ChapterHeader(
                    chapterCount = chapterCount,
                    isChapterFiltered = chapterFiltered,
                    onFilterButtonClicked = onFilterButtonClicked,
                )
            }

            MangaSmallAppBar(
                modifier = Modifier
                    .layoutId("topBar")
                    .onSizeChanged {
                        onSmallHeightPxChanged(it.height)
                        onSmallAppBarHeightChanged(it.height)
                    },
                title = title,
                titleAlphaProvider = { if (actionModeCounter == 0) scrollPercentageProvider() else 1f },
                incognitoMode = incognitoMode,
                downloadedOnlyMode = downloadedOnlyMode,
                onBackClicked = onBackClicked,
                onShareClicked = onShareClicked,
                onDownloadClicked = onDownloadClicked,
                onEditCategoryClicked = onEditCategoryClicked,
                onMigrateClicked = onMigrateClicked,
                actionModeCounter = actionModeCounter,
                onSelectAll = onSelectAll,
                onInvertSelection = onInvertSelection,
            )
        },
    ) { measurables, constraints ->
        val mangaInfoPlaceable = measurables
            .first { it.layoutId == "mangaInfo" }
            .measure(constraints.copy(maxHeight = Constraints.Infinity))
        val topBarPlaceable = measurables
            .first { it.layoutId == "topBar" }
            .measure(constraints)
        val mangaInfoHeight = mangaInfoPlaceable.height
        val topBarHeight = topBarPlaceable.height
        val mangaInfoSansTopBarHeightPx = mangaInfoHeight - topBarHeight
        val layoutHeight = topBarHeight +
            (mangaInfoSansTopBarHeightPx * inverseScrollPercentageProvider()).roundToInt()

        layout(constraints.maxWidth, layoutHeight) {
            val mangaInfoY = (-mangaInfoSansTopBarHeightPx * scrollPercentageProvider()).roundToInt()
            mangaInfoPlaceable.place(0, mangaInfoY)
            topBarPlaceable.place(0, 0)

            // Update offset limit
            val offsetLimit = -mangaInfoSansTopBarHeightPx.toFloat()
            if (scrollBehavior?.state?.offsetLimit != offsetLimit) {
                scrollBehavior?.state?.offsetLimit = offsetLimit
            }
        }
    }
}
