package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Slider
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

@Composable
fun NovelReaderAppBars(
    visible: Boolean,

    // Top bar
    novelTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onReloadLocal: () -> Unit,
    onReloadSource: () -> Unit,

    // Progress slider
    showProgressSlider: Boolean,
    currentProgress: Int, // 0-100 percentage
    onProgressChange: (Int) -> Unit,

    // Bottom bar - navigation
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,

    // Bottom bar - actions
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    onClickSettings: () -> Unit,
    onScrollToTop: () -> Unit,
    isAutoScrolling: Boolean,
    onToggleAutoScroll: () -> Unit,
) {
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
            NovelReaderTopBar(
                modifier = Modifier
                    .background(backgroundColor)
                    .clickable(onClick = onClickTopAppBar),
                novelTitle = novelTitle,
                chapterTitle = chapterTitle,
                navigateUp = navigateUp,
                bookmarked = bookmarked,
                onToggleBookmarked = onToggleBookmarked,
                onOpenInWebView = onOpenInWebView,
                onOpenInBrowser = onOpenInBrowser,
                onShare = onShare,
                onReloadLocal = onReloadLocal,
                onReloadSource = onReloadSource,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeIn(animationSpec = readerBarsFadeAnimationSpec),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeOut(animationSpec = readerBarsFadeAnimationSpec),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                // Progress slider (above bottom bar)
                if (showProgressSlider) {
                    NovelProgressSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                        currentProgress = currentProgress,
                        onProgressChange = onProgressChange,
                        backgroundColor = backgroundColor,
                    )
                }

                NovelReaderBottomBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.small),
                    onNextChapter = onNextChapter,
                    enabledNext = enabledNext,
                    onPreviousChapter = onPreviousChapter,
                    enabledPrevious = enabledPrevious,
                    orientation = orientation,
                    onClickOrientation = onClickOrientation,
                    onClickSettings = onClickSettings,
                    onScrollToTop = onScrollToTop,
                    isAutoScrolling = isAutoScrolling,
                    onToggleAutoScroll = onToggleAutoScroll,
                )
            }
        }
    }
}

@Composable
private fun NovelReaderTopBar(
    novelTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onReloadLocal: () -> Unit,
    onReloadSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        title = novelTitle,
        subtitle = chapterTitle,
        navigateUp = navigateUp,
        actions = {
            AppBarActions(
                actions = persistentListOf<AppBar.AppBarAction>().builder()
                    .apply {
                        add(
                            AppBar.Action(
                                title = stringResource(
                                    if (bookmarked) {
                                        MR.strings.action_remove_bookmark
                                    } else {
                                        MR.strings.action_bookmark
                                    },
                                ),
                                icon = if (bookmarked) {
                                    Icons.Outlined.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                onClick = onToggleBookmarked,
                            ),
                        )
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_reload_local),
                                onClick = onReloadLocal,
                            ),
                        )
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_reload_source),
                                onClick = onReloadSource,
                            ),
                        )
                        onOpenInWebView?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_open_in_web_view),
                                    onClick = it,
                                ),
                            )
                        }
                        onOpenInBrowser?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_open_in_browser),
                                    onClick = it,
                                ),
                            )
                        }
                        onShare?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_share),
                                    onClick = it,
                                ),
                            )
                        }
                    }
                    .build(),
            )
        },
    )
}

@Composable
private fun NovelReaderBottomBar(
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    onClickSettings: () -> Unit,
    onScrollToTop: () -> Unit,
    isAutoScrolling: Boolean,
    onToggleAutoScroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Previous chapter - left position, left arrow icon
        IconButton(
            onClick = onPreviousChapter,
            enabled = enabledPrevious,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.NavigateBefore,
                contentDescription = stringResource(MR.strings.action_previous_chapter),
            )
        }

        // Scroll to top
        IconButton(onClick = onScrollToTop) {
            Icon(
                imageVector = Icons.Outlined.VerticalAlignTop,
                contentDescription = stringResource(MR.strings.action_scroll_to_top),
            )
        }

        // Auto-scroll toggle
        IconButton(onClick = onToggleAutoScroll) {
            Icon(
                imageVector = if (isAutoScrolling) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = stringResource(
                    if (isAutoScrolling) MR.strings.action_stop_auto_scroll else MR.strings.action_start_auto_scroll,
                ),
            )
        }

        // Orientation
        IconButton(onClick = onClickOrientation) {
            Icon(
                imageVector = orientation.icon,
                contentDescription = stringResource(MR.strings.rotation_type),
            )
        }

        // Settings
        IconButton(onClick = onClickSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(MR.strings.action_settings),
            )
        }

        // Next chapter - right position, right arrow icon
        IconButton(
            onClick = onNextChapter,
            enabled = enabledNext,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = stringResource(MR.strings.action_next_chapter),
            )
        }
    }
}

@Composable
private fun NovelProgressSlider(
    currentProgress: Int,
    onProgressChange: (Int) -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val sliderDragged by interactionSource.collectIsDraggedAsState()

    LaunchedEffect(currentProgress) {
        if (sliderDragged) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Current progress percentage
        Box(contentAlignment = Alignment.CenterEnd) {
            Text(text = "$currentProgress%")
            // Taking up full length so the slider doesn't shift
            Text(text = "100%", color = Color.Transparent)
        }

        Slider(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            value = currentProgress,
            valueRange = 0..100,
            onValueChange = { newProgress ->
                if (newProgress != currentProgress) {
                    onProgressChange(newProgress)
                }
            },
            interactionSource = interactionSource,
        )

        Text(text = "100%")
    }
}
