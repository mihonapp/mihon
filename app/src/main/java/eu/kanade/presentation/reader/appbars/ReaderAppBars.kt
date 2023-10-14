package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer

private val animationSpec = tween<IntOffset>(200)

@Composable
fun ReaderAppBars(
    visible: Boolean,
    fullscreen: Boolean,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onShare: (() -> Unit)?,

    viewer: Viewer?,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onSliderValueChange: (Int) -> Unit,

    readingMode: ReadingModeType,
    onClickReadingMode: () -> Unit,
    orientationMode: OrientationType,
    onClickOrientationMode: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
) {
    val isRtl = viewer is R2LPagerViewer
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

    val appBarModifier = if (fullscreen) {
        Modifier.windowInsetsPadding(WindowInsets.systemBars)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = animationSpec,
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = animationSpec,
            ),
        ) {
            AppBar(
                modifier = appBarModifier
                    .clickable(onClick = onClickTopAppBar),
                backgroundColor = backgroundColor,
                title = mangaTitle,
                subtitle = chapterTitle,
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        listOfNotNull(
                            AppBar.Action(
                                title = stringResource(if (bookmarked) R.string.action_remove_bookmark else R.string.action_bookmark),
                                icon = if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                onClick = onToggleBookmarked,
                            ),
                            onOpenInWebView?.let {
                                AppBar.OverflowAction(
                                    title = stringResource(R.string.action_open_in_web_view),
                                    onClick = it,
                                )
                            },
                            onShare?.let {
                                AppBar.OverflowAction(
                                    title = stringResource(R.string.action_share),
                                    onClick = it,
                                )
                            },
                        ),
                    )
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = animationSpec,
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = animationSpec,
            ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChapterNavigator(
                    isRtl = isRtl,
                    onNextChapter = onNextChapter,
                    enabledNext = enabledNext,
                    onPreviousChapter = onPreviousChapter,
                    enabledPrevious = enabledPrevious,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onSliderValueChange = onSliderValueChange,
                )

                BottomReaderBar(
                    backgroundColor = backgroundColor,
                    readingMode = readingMode,
                    onClickReadingMode = onClickReadingMode,
                    orientationMode = orientationMode,
                    onClickOrientationMode = onClickOrientationMode,
                    cropEnabled = cropEnabled,
                    onClickCropBorder = onClickCropBorder,
                    onClickSettings = onClickSettings,
                )
            }
        }
    }
}
