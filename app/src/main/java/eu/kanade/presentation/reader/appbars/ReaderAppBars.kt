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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ReaderStatusOverlay
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.presentation.core.components.material.padding

// E-ink: Disable animations for instant transitions
private val readerBarsAnimationSpec = tween<IntOffset>(0)  // 0ms = instant
private val readerFadeAnimationSpec = tween<Float>(0)      // 0ms = instant

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

    chapterNavigatorType: ChapterNavigatorType,
    verticalNavigatorHeight: Float,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    totalChapters: Int,
    onPageIndexChange: (Int) -> Unit,
    onPageIndexChangeFinished: () -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,

    // E-ink: Show custom status overlay
    showStatusOverlay: Boolean = true,
) {
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

    Box(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxHeight()) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = readerBarsAnimationSpec) +
                    fadeIn(animationSpec = readerFadeAnimationSpec),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = readerBarsAnimationSpec) +
                    fadeOut(animationSpec = readerFadeAnimationSpec),
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

            if (!chapterNavigatorType.isHorizontal()) {
                val sliderOnLeft = chapterNavigatorType == ChapterNavigatorType.VERTICAL_LEFT
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (sliderOnLeft) LayoutDirection.Ltr else LayoutDirection.Rtl,
                ) {
                    Row(modifier = Modifier.weight(1f)) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInHorizontally(readerBarsAnimationSpec) { if (sliderOnLeft) -it else it } +
                                fadeIn(readerFadeAnimationSpec),
                            exit = slideOutHorizontally(readerBarsAnimationSpec) { if (sliderOnLeft) -it else it } +
                                fadeOut(readerFadeAnimationSpec),
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                                Box(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    ChapterNavigator(
                                        modifier = Modifier.fillMaxHeight(verticalNavigatorHeight),
                                        type = chapterNavigatorType,
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
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // E-ink: Custom status overlay (time, battery, page indicator)
            // Show overlay by default during reading when menu is visible (as a status bar above bottom bar)
            if (showStatusOverlay) {
                ReaderStatusOverlay(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    currentChapter = chapterTitle ?: "Chapter",
                    totalChapters = totalChapters,
                    visible = true,  // Always show during reading
                )
            }

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsAnimationSpec) +
                    fadeIn(animationSpec = readerFadeAnimationSpec),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsAnimationSpec) +
                    fadeOut(animationSpec = readerFadeAnimationSpec),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    if (chapterNavigatorType.isHorizontal()) {
                        ChapterNavigator(
                            type = chapterNavigatorType,
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
                    )
                }
            }
        }
    }
}
