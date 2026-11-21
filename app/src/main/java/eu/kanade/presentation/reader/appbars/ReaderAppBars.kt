package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import tachiyomi.presentation.core.components.material.padding

private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

enum class NavBarType {
    VerticalRight,
    VerticalLeft,
    Bottom,
}

@Composable
fun ReaderAppBars(
    visible: Boolean,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,

    viewer: Viewer?,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    onPageIndexChangeFinished: () -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    navBarType: NavBarType = NavBarType.Bottom,
    autoScrolling: Boolean? = null,
    onClickAutoScroll: () -> Unit = {},
) {
    val isRtl = viewer is R2LPagerViewer
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

    Column(modifier = Modifier.fillMaxHeight()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeIn(animationSpec = readerBarsFadeAnimationSpec),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeOut(animationSpec = readerBarsFadeAnimationSpec),
        ) {
            ReaderTopBar(
                modifier = Modifier
                    .background(backgroundColor)
                    .clickable(onClick = onClickTopAppBar),
                mangaTitle = mangaTitle,
                chapterTitle = chapterTitle,
                navigateUp = navigateUp,
                bookmarked = bookmarked,
                onToggleBookmarked = onToggleBookmarked,
                onOpenInWebView = onOpenInWebView,
                onOpenInBrowser = onOpenInBrowser,
                onShare = onShare,
            )
        }

        // The vertical navigator fills exactly the space left between the top and bottom bars, on whichever
        // physical side was picked regardless of the app's layout direction
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
                ),
        ) {
            if (navBarType != NavBarType.Bottom) {
                VerticalChapterNavigator(
                    visible = visible,
                    onLeft = navBarType == NavBarType.VerticalLeft,
                    onNextChapter = onNextChapter,
                    enabledNext = enabledNext,
                    onPreviousChapter = onPreviousChapter,
                    enabledPrevious = enabledPrevious,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onPageIndexChange = onPageIndexChange,
                    onPageIndexChangeFinished = onPageIndexChangeFinished,
                )
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeIn(animationSpec = readerBarsFadeAnimationSpec),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeOut(animationSpec = readerBarsFadeAnimationSpec),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                if (navBarType == NavBarType.Bottom) {
                    ChapterNavigator(
                        isRtl = isRtl,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                        onPageIndexChangeFinished = onPageIndexChangeFinished,
                    )
                }
                ReaderBottomBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = MaterialTheme.padding.small)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    readingMode = readingMode,
                    onClickReadingMode = onClickReadingMode,
                    orientation = orientation,
                    onClickOrientation = onClickOrientation,
                    cropEnabled = cropEnabled,
                    onClickCropBorder = onClickCropBorder,
                    onClickSettings = onClickSettings,
                    autoScrolling = autoScrolling,
                    onClickAutoScroll = onClickAutoScroll,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.VerticalChapterNavigator(
    visible: Boolean,
    onLeft: Boolean,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    onPageIndexChangeFinished: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { if (onLeft) -it else it },
            animationSpec = readerBarsSlideAnimationSpec,
        ) + fadeIn(animationSpec = readerBarsFadeAnimationSpec),
        exit = slideOutHorizontally(
            targetOffsetX = { if (onLeft) -it else it },
            animationSpec = readerBarsSlideAnimationSpec,
        ) + fadeOut(animationSpec = readerBarsFadeAnimationSpec),
        modifier = Modifier
            .fillMaxHeight()
            .align(if (onLeft) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight),
    ) {
        ChapterNavigator(
            isRtl = false,
            isVerticalSlider = true,
            onNextChapter = onNextChapter,
            enabledNext = enabledNext,
            onPreviousChapter = onPreviousChapter,
            enabledPrevious = enabledPrevious,
            currentPage = currentPage,
            totalPages = totalPages,
            onPageIndexChange = onPageIndexChange,
            onPageIndexChangeFinished = onPageIndexChangeFinished,
        )
    }
}
