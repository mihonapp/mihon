package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.ReadItemAlpha
import tachiyomi.presentation.core.components.material.SecondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground

@Composable
fun MangaChapterListItem(
    modifier: Modifier = Modifier,
    title: String,
    date: String?,
    readProgress: String?,
    scanlator: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
) {
    // Increase touch slop of swipe action to reduce accidental trigger
    val configuration = LocalViewConfiguration.current
    CompositionLocalProvider(
        LocalViewConfiguration provides object : ViewConfiguration by configuration {
            override val touchSlop: Float = configuration.touchSlop * 3f
        },
    ) {
        val textAlpha = if (read) ReadItemAlpha else 1f
        val textSubtitleAlpha = if (read) ReadItemAlpha else SecondaryItemAlpha

        val chapterSwipeStartEnabled = chapterSwipeStartAction != LibraryPreferences.ChapterSwipeAction.Disabled
        val chapterSwipeEndEnabled = chapterSwipeEndAction != LibraryPreferences.ChapterSwipeAction.Disabled

        val dismissState = rememberDismissState()
        val dismissDirections = remember { mutableSetOf<DismissDirection>() }
        var lastDismissDirection: DismissDirection? by remember { mutableStateOf(null) }
        if (lastDismissDirection == null) {
            if (chapterSwipeStartEnabled) {
                dismissDirections.add(DismissDirection.EndToStart)
            }
            if (chapterSwipeEndEnabled) {
                dismissDirections.add(DismissDirection.StartToEnd)
            }
        }
        val animateDismissContentAlpha by animateFloatAsState(
            label = "animateDismissContentAlpha",
            targetValue = if (lastDismissDirection != null) 1f else 0f,
            animationSpec = tween(durationMillis = if (lastDismissDirection != null) 500 else 0),
            finishedListener = {
                lastDismissDirection = null
            },
        )
        val dismissContentAlpha = if (lastDismissDirection != null) animateDismissContentAlpha else 1f
        val backgroundColor = if (chapterSwipeEndEnabled && (dismissState.dismissDirection == DismissDirection.StartToEnd || lastDismissDirection == DismissDirection.StartToEnd)) {
            MaterialTheme.colorScheme.primary
        } else if (chapterSwipeStartEnabled && (dismissState.dismissDirection == DismissDirection.EndToStart || lastDismissDirection == DismissDirection.EndToStart)) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Unspecified
        }

        LaunchedEffect(dismissState.currentValue) {
            when (dismissState.currentValue) {
                DismissValue.DismissedToEnd -> {
                    lastDismissDirection = DismissDirection.StartToEnd
                    val dismissDirectionsCopy = dismissDirections.toSet()
                    dismissDirections.clear()
                    onChapterSwipe(chapterSwipeEndAction)
                    dismissState.snapTo(DismissValue.Default)
                    dismissDirections.addAll(dismissDirectionsCopy)
                }
                DismissValue.DismissedToStart -> {
                    lastDismissDirection = DismissDirection.EndToStart
                    val dismissDirectionsCopy = dismissDirections.toSet()
                    dismissDirections.clear()
                    onChapterSwipe(chapterSwipeStartAction)
                    dismissState.snapTo(DismissValue.Default)
                    dismissDirections.addAll(dismissDirectionsCopy)
                }
                DismissValue.Default -> { }
            }
        }

        SwipeToDismiss(
            state = dismissState,
            directions = dismissDirections,
            background = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                ) {
                    if (dismissState.dismissDirection in dismissDirections) {
                        val downloadState = downloadStateProvider()
                        SwipeBackgroundIcon(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .align(Alignment.CenterStart)
                                .alpha(
                                    if (dismissState.dismissDirection == DismissDirection.StartToEnd) 1f else 0f,
                                ),
                            tint = contentColorFor(backgroundColor),
                            swipeAction = chapterSwipeEndAction,
                            read = read,
                            bookmark = bookmark,
                            downloadState = downloadState,
                        )
                        SwipeBackgroundIcon(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .align(Alignment.CenterEnd)
                                .alpha(
                                    if (dismissState.dismissDirection == DismissDirection.EndToStart) 1f else 0f,
                                ),
                            tint = contentColorFor(backgroundColor),
                            swipeAction = chapterSwipeStartAction,
                            read = read,
                            bookmark = bookmark,
                            downloadState = downloadState,
                        )
                    }
                }
            },
            dismissContent = {
                Row(
                    modifier = modifier
                        .background(
                            MaterialTheme.colorScheme.background.copy(dismissContentAlpha),
                        )
                        .selectedBackground(selected)
                        .alpha(dismissContentAlpha)
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                        .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            var textHeight by remember { mutableIntStateOf(0) }
                            if (!read) {
                                Icon(
                                    imageVector = Icons.Filled.Circle,
                                    contentDescription = stringResource(R.string.unread),
                                    modifier = Modifier
                                        .height(8.dp)
                                        .padding(end = 4.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (bookmark) {
                                Icon(
                                    imageVector = Icons.Filled.Bookmark,
                                    contentDescription = stringResource(R.string.action_filter_bookmarked),
                                    modifier = Modifier
                                        .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = textAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { textHeight = it.size.height },
                            )
                        }

                        Row {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    color = LocalContentColor.current.copy(alpha = textSubtitleAlpha),
                                ),
                            ) {
                                if (date != null) {
                                    Text(
                                        text = date,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (readProgress != null || scanlator != null) DotSeparatorText()
                                }
                                if (readProgress != null) {
                                    Text(
                                        text = readProgress,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.alpha(ReadItemAlpha),
                                    )
                                    if (scanlator != null) DotSeparatorText()
                                }
                                if (scanlator != null) {
                                    Text(
                                        text = scanlator,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    if (onDownloadClick != null) {
                        ChapterDownloadIndicator(
                            enabled = downloadIndicatorEnabled,
                            modifier = Modifier.padding(start = 4.dp),
                            downloadStateProvider = downloadStateProvider,
                            downloadProgressProvider = downloadProgressProvider,
                            onClick = onDownloadClick,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SwipeBackgroundIcon(
    modifier: Modifier = Modifier,
    tint: Color,
    swipeAction: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
) {
    val imageVector = when (swipeAction) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
            if (!read) {
                Icons.Outlined.Done
            } else {
                Icons.Outlined.RemoveDone
            }
        }
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
            if (!bookmark) {
                Icons.Outlined.BookmarkAdd
            } else {
                Icons.Outlined.BookmarkRemove
            }
        }
        LibraryPreferences.ChapterSwipeAction.Download -> {
            when (downloadState) {
                Download.State.NOT_DOWNLOADED,
                Download.State.ERROR,
                -> { Icons.Outlined.Download }
                Download.State.QUEUE,
                Download.State.DOWNLOADING,
                -> { Icons.Outlined.FileDownloadOff }
                Download.State.DOWNLOADED -> { Icons.Outlined.Delete }
            }
        }
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
    }
    imageVector?.let {
        Icon(
            modifier = modifier,
            imageVector = imageVector,
            tint = tint,
            contentDescription = null,
        )
    }
}
