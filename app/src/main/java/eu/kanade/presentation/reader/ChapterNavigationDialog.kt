package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs

@Composable
fun ChapterNavigationDialog(
    chapters: List<ReaderViewModel.ChapterNavigationItem>,
    direction: ReaderViewModel.ChapterNavigationDirection,
    mangaDisplayMode: Long,
    currentChapterNumber: Double?,
    onDismissRequest: () -> Unit,
    onChapterClick: (Long) -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        BoxWithConstraints {
            val title = stringResource(
                when (direction) {
                    ReaderViewModel.ChapterNavigationDirection.Previous -> MR.strings.label_previous_chapters
                    ReaderViewModel.ChapterNavigationDirection.Next -> MR.strings.label_next_chapters
                },
            )
            val initialItemIndex = remember(chapters, currentChapterNumber) {
                if (chapters.isEmpty() || currentChapterNumber == null) {
                    0
                } else {
                    chapters.indices.minByOrNull { index ->
                        abs(chapters[index].chapter.chapterNumber - currentChapterNumber)
                    } ?: 0
                }
            }
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialItemIndex,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.75f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                LazyColumn(
                    state = listState,
                ) {
                    items(
                        items = chapters,
                        key = { it.chapter.id },
                    ) { chapterItem ->
                        val chapter = chapterItem.chapter
                        MangaChapterListItem(
                            title = if (mangaDisplayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                                stringResource(
                                    MR.strings.display_mode_chapter,
                                    formatChapterNumber(chapter.chapterNumber),
                                )
                            } else {
                                chapter.name
                            },
                            date = relativeDateText(chapter.dateUpload),
                            readProgress = chapter.lastPageRead
                                .takeIf { !chapter.read && it > 0L }
                                ?.let {
                                    stringResource(
                                        MR.strings.chapter_progress,
                                        it + 1,
                                    )
                                },
                            scanlator = chapter.scanlator.takeIf { !it.isNullOrBlank() },
                            read = chapter.read,
                            bookmark = chapter.bookmark,
                            selected = false,
                            showDownloadIndicator = chapterItem.downloadState == Download.State.DOWNLOADED ||
                                chapterItem.downloadState == Download.State.QUEUE ||
                                chapterItem.downloadState == Download.State.DOWNLOADING,
                            downloadIndicatorEnabled = false,
                            downloadStateProvider = { chapterItem.downloadState },
                            downloadProgressProvider = { chapterItem.downloadProgress },
                            chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.Disabled,
                            chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.Disabled,
                            onLongClick = {},
                            onClick = { onChapterClick(chapter.id) },
                            onDownloadClick = null,
                            onChapterSwipe = {},
                        )
                    }
                }
            }
        }
    }
}
