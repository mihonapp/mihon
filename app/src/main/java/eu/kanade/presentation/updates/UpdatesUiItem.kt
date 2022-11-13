package eu.kanade.presentation.updates

import android.text.format.DateUtils
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.ChapterDownloadIndicator
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.components.RelativeDateHeader
import eu.kanade.presentation.util.ReadItemAlpha
import eu.kanade.presentation.util.padding
import eu.kanade.presentation.util.selectedBackground
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import java.text.DateFormat
import java.util.Date
import kotlin.time.Duration.Companion.minutes

fun LazyListScope.updatesLastUpdatedItem(
    lastUpdated: Long,
) {
    item(key = "updates-lastUpdated") {
        val time = remember(lastUpdated) {
            val now = Date().time
            if (now - lastUpdated < 1.minutes.inWholeMilliseconds) {
                null
            } else {
                DateUtils.getRelativeTimeSpanString(lastUpdated, now, DateUtils.MINUTE_IN_MILLIS)
            }
        }

        Box(
            modifier = Modifier
                .animateItemPlacement()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        ) {
            Text(
                text = if (time.isNullOrEmpty()) {
                    stringResource(R.string.updates_last_update_info, stringResource(R.string.updates_last_update_info_just_now))
                } else {
                    stringResource(R.string.updates_last_update_info, time)
                },
                style = LocalTextStyle.current.copy(
                    fontStyle = FontStyle.Italic,
                ),
            )
        }
    }
}

fun LazyListScope.updatesUiItems(
    uiModels: List<UpdatesUiModel>,
    selectionMode: Boolean,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean, Boolean) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    relativeTime: Int,
    dateFormat: DateFormat,
) {
    items(
        items = uiModels,
        contentType = {
            when (it) {
                is UpdatesUiModel.Header -> "header"
                is UpdatesUiModel.Item -> "item"
            }
        },
        key = {
            when (it) {
                is UpdatesUiModel.Header -> "updatesHeader-${it.hashCode()}"
                is UpdatesUiModel.Item -> "updates-${it.item.update.mangaId}-${it.item.update.chapterId}"
            }
        },
    ) { item ->
        when (item) {
            is UpdatesUiModel.Header -> {
                RelativeDateHeader(
                    modifier = Modifier.animateItemPlacement(),
                    date = item.date,
                    relativeTime = relativeTime,
                    dateFormat = dateFormat,
                )
            }
            is UpdatesUiModel.Item -> {
                val updatesItem = item.item
                UpdatesUiItem(
                    modifier = Modifier.animateItemPlacement(),
                    update = updatesItem.update,
                    selected = updatesItem.selected,
                    onLongClick = {
                        onUpdateSelected(updatesItem, !updatesItem.selected, true, true)
                    },
                    onClick = {
                        when {
                            selectionMode -> onUpdateSelected(updatesItem, !updatesItem.selected, true, false)
                            else -> onClickUpdate(updatesItem)
                        }
                    },
                    onClickCover = { if (selectionMode.not()) onClickCover(updatesItem) },
                    onDownloadChapter = {
                        if (selectionMode.not()) onDownloadChapter(listOf(updatesItem), it)
                    },
                    downloadIndicatorEnabled = selectionMode.not(),
                    downloadStateProvider = updatesItem.downloadStateProvider,
                    downloadProgressProvider = updatesItem.downloadProgressProvider,
                )
            }
        }
    }
}

@Composable
fun UpdatesUiItem(
    modifier: Modifier,
    update: UpdatesWithRelations,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: () -> Unit,
    onDownloadChapter: (ChapterDownloadAction) -> Unit,
    // Download Indicator
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
) {
    val haptic = LocalHapticFeedback.current
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
            val bookmark = remember(update.bookmark) { update.bookmark }
            val read = remember(update.read) { update.read }

            val textAlpha = remember(read) { if (read) ReadItemAlpha else 1f }

            val secondaryTextColor = if (bookmark && !read) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Text(
                text = update.mangaTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(textAlpha),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                var textHeight by remember { mutableStateOf(0) }
                if (bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.action_filter_bookmarked),
                        modifier = Modifier
                            .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = update.chapterName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                        .copy(color = secondaryTextColor),
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textHeight = it.size.height },
                    modifier = Modifier.alpha(textAlpha),
                )
            }
        }
        ChapterDownloadIndicator(
            enabled = downloadIndicatorEnabled,
            modifier = Modifier.padding(start = 4.dp),
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = onDownloadChapter,
        )
    }
}
