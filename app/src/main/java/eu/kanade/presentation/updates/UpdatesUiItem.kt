package eu.kanade.presentation.updates

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.manga.components.DotSeparatorText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

internal fun LazyListScope.updatesLastUpdatedItem(
    lastUpdated: Long,
) {
    item(key = "updates-lastUpdated") {
        Box(
            modifier = Modifier
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(MR.strings.updates_last_update_info, relativeTimeSpanString(lastUpdated)),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

internal fun LazyListScope.updatesUiItems(
    uiModels: List<UpdatesUiModel>,
    selectionMode: Boolean,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
) {
    items(
        items = uiModels,
        contentType = {
            when (it) {
                is UpdatesUiModel.Header -> "header"
                is UpdatesUiModel.Item -> "item"
                is UpdatesUiModel.Group -> "group"
            }
        },
        key = {
            when (it) {
                is UpdatesUiModel.Header -> "updatesHeader-${it.hashCode()}"
                is UpdatesUiModel.Item -> "updates-${it.item.update.mangaId}-${it.item.update.chapterId}"
                is UpdatesUiModel.Group -> "groups-${it.mangaId}-${it.groupDate}"
            }
        },
    ) { item ->
        when (item) {
            is UpdatesUiModel.Header -> {
                ListGroupHeader(
                    modifier = Modifier.animateItemFastScroll(),
                    text = relativeDateText(item.date),
                )
            }

            is UpdatesUiModel.Item -> {
                val updatesItem = item.item
                UpdatesUiItem(
                    modifier = Modifier.animateItemFastScroll(),
                    update = updatesItem.update,
                    selected = updatesItem.selected,
                    readProgress = updatesItem.update.lastPageRead
                        .takeIf { !updatesItem.update.read && it > 0L }
                        ?.let {
                            stringResource(
                                MR.strings.chapter_progress,
                                it + 1,
                            )
                        },
                    onLongClick = {
                        onUpdateSelected(updatesItem, !updatesItem.selected, true)
                    },
                    onClick = {
                        when {
                            selectionMode -> onUpdateSelected(updatesItem, !updatesItem.selected, false)
                            else -> onClickUpdate(updatesItem)
                        }
                    },
                    onClickCover = { onClickCover(updatesItem) }.takeIf { !selectionMode },
                    onDownloadChapter = { action: ChapterDownloadAction ->
                        onDownloadChapter(listOf(updatesItem), action)
                    }.takeIf { !selectionMode },
                    downloadStateProvider = updatesItem.downloadStateProvider,
                    downloadProgressProvider = updatesItem.downloadProgressProvider,
                )
            }

            is UpdatesUiModel.Group -> {
                GroupedUpdatesUiItem(
                    modifier = Modifier.animateItemFastScroll(),
                    updateModelItems = item.items,
                    selectionMode = selectionMode,
                    onUpdateSelected = onUpdateSelected,
                    onClickCover = { onClickCover(item.items.first().item).takeIf { !selectionMode } },
                    onClickUpdate = onClickUpdate,
                    onDownloadChapter = onDownloadChapter,
                )
            }
        }
    }
}

@Composable
private fun UpdatesUiItem(
    update: UpdatesWithRelations,
    selected: Boolean,
    readProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onDownloadChapter: ((ChapterDownloadAction) -> Unit)?,
    // Download Indicator
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (update.read) DISABLED_ALPHA else 1f

    Row(
        modifier = modifier
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxHeight(),
            data = update.coverData,
            onClick = onClickCover,
        )

        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = update.mangaTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = textAlpha),
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                var textHeight by remember { mutableIntStateOf(0) }
                if (!update.read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = stringResource(MR.strings.unread),
                        modifier = Modifier
                            .height(8.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (update.bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                        modifier = Modifier
                            .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = update.chapterName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textHeight = it.size.height },
                    modifier = Modifier
                        .weight(weight = 1f, fill = false),
                )
                if (readProgress != null) {
                    DotSeparatorText()
                    Text(
                        text = readProgress,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ChapterDownloadIndicator(
            enabled = onDownloadChapter != null,
            modifier = Modifier.padding(start = 4.dp),
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = { onDownloadChapter?.invoke(it) },
        )
    }
}

@Composable
private fun GroupedUpdatesUiItem(
    modifier: Modifier = Modifier,
    updateModelItems: List<UpdatesUiModel.Item>,
    selectionMode: Boolean,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean) -> Unit,
    onClickCover: (() -> Unit)?,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (updateModelItems.all { it.item.update.read }) DISABLED_ALPHA else 1f

    var expanded by rememberSaveable { mutableStateOf(false) }
    val isExpandable = updateModelItems.size > 2

    val firstItem = updateModelItems.first().item
    val firstUpdate = firstItem.update

    val chapterItem: @Composable (UpdatesUiModel.Item) -> Unit = { modelItem ->
        val item = modelItem.item
        GroupedChapterItem(
            update = item.update,
            selected = item.selected,
            readProgress = item.update.lastPageRead.takeIf { !item.update.read && it > 0L }
                ?.let { stringResource(MR.strings.chapter_progress, it + 1) },
            onClick = {
                when {
                    selectionMode -> onUpdateSelected(item, !item.selected, false)
                    else -> onClickUpdate(item)
                }
            },
            onLongClick = { onUpdateSelected(item, !item.selected, true) },
            onDownloadChapter = { action: ChapterDownloadAction ->
                onDownloadChapter(listOf(item), action)
            }.takeIf { !selectionMode },
            downloadStateProvider = item.downloadStateProvider,
            downloadProgressProvider = item.downloadProgressProvider,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small)
            .combinedClickable(
                onClick = { if (isExpandable) expanded = !expanded },
                onLongClick = {
                    updateModelItems.forEach {
                        onUpdateSelected(it.item, !it.item.selected, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (isExpandable) expanded = true
                },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        MangaCover.Book(
            modifier = Modifier
                .padding(top = MaterialTheme.padding.extraSmall)
                .height(66.dp),
            data = firstUpdate.coverData,
            onClick = onClickCover,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MaterialTheme.padding.extraLarge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = firstUpdate.mangaTitle,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .weight(1f),
                )
                if (isExpandable) {
                    val offset by animateDpAsState(
                        targetValue = if (expanded) (-4).dp else 0.dp,
                    )
                    val painter = rememberAnimatedVectorPainter(
                        AnimatedImageVector.animatedVectorResource(R.drawable.anim_caret_down),
                        !expanded,
                    )
                    Icon(
                        painter = painter,
                        tint = MaterialTheme.colorScheme.onBackground,
                        contentDescription = stringResource(
                            if (expanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand,
                        ),
                        modifier = Modifier
                            .offset { IntOffset(x = 0, y = offset.roundToPx()) },
                    )
                }
            }
            chapterItem(updateModelItems.first())
            updateModelItems.drop(1)
                .let { modelItems ->
                    if (modelItems.isEmpty()) return@let
                    if (modelItems.size == 1) {
                        chapterItem(modelItems.first())
                    } else {
                        AnimatedContent(
                            targetState = expanded,
                            transitionSpec = {
                                if (targetState) {
                                    (fadeIn() + expandVertically()) togetherWith (fadeOut())
                                } else {
                                    fadeIn() togetherWith (fadeOut() + shrinkVertically())
                                } using SizeTransform(clip = false)
                            },
                        ) { isExpanded ->
                            if (isExpanded) {
                                Column {
                                    modelItems.forEach { chapterItem(it) }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 48.dp,
                                            top = 6.dp,
                                            bottom = 6.dp,
                                        ),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = pluralStringResource(
                                            MR.plurals.updates_collapsed_chapters,
                                            modelItems.size,
                                            modelItems.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(
                                            alpha = textAlpha.coerceAtMost(SECONDARY_ALPHA),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun GroupedChapterItem(
    update: UpdatesWithRelations,
    selected: Boolean,
    readProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadChapter: ((ChapterDownloadAction) -> Unit)?,
    // Download Indicator
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (update.read) DISABLED_ALPHA else 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MaterialTheme.padding.extraLarge)
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var textHeight by remember { mutableIntStateOf(0) }
            if (!update.read) {
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = stringResource(MR.strings.unread),
                    modifier = Modifier
                        .height(8.dp)
                        .padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (update.bookmark) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                    modifier = Modifier
                        .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = update.chapterName,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = textAlpha),
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textHeight = it.size.height },
            )
            if (readProgress != null) {
                DotSeparatorText()
                Text(
                    text = readProgress,
                    maxLines = 1,
                    color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                )
            }
        }
        ChapterDownloadIndicator(
            enabled = onDownloadChapter != null,
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = { onDownloadChapter?.invoke(it) },
        )
    }
}
