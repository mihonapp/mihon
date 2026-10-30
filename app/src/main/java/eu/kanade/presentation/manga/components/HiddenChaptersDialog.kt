package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.tachiyomi.ui.manga.MangaChapterSelection
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.SelectAll
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun HiddenChaptersDialog(
    manga: Manga,
    chapters: List<ChapterList.Item>,
    snackbarHostState: SnackbarHostState,
    onDismissRequest: () -> Unit,
    onUnhide: (List<Chapter>) -> Unit,
) {
    // This surface has its own selection, discarded when it closes.
    val selection = remember { MangaChapterSelection() }
    val selectedIds by selection.ids.collectAsState()
    val rows = remember(chapters, manga) {
        val sort = getChapterSort(manga)
        chapters.filter { it.chapter.hidden }.sortedWith { a, b -> sort(a.chapter, b.chapter) }
    }
    LaunchedEffect(rows) { selection.retain(rows) }
    val selected = rows.filter { it.id in selectedIds }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Scaffold(
            modifier = Modifier.fillMaxHeight(0.85f),
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.hidden_chapters),
                    navigateUp = onDismissRequest,
                    actions = {
                        if (rows.isNotEmpty()) {
                            AppBarActions(
                                listOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_select_all),
                                        icon = MaterialSymbols.Rounded.SelectAll,
                                        onClick = { selection.selectAll(rows, true) },
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_select_inverse),
                                        icon = MaterialSymbols.Rounded.FlipToBack,
                                        onClick = { selection.invert(rows) },
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { onUnhide(selected.map { it.chapter }) },
                    ) {
                        Text(stringResource(MR.strings.action_unhide))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(MR.strings.hidden_chapters_empty))
                }
            } else {
                LazyColumn(contentPadding = padding) {
                    items(rows, key = { it.id }) { item ->
                        MangaChapterListItem(
                            title = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                                stringResource(
                                    MR.strings.display_mode_chapter,
                                    formatChapterNumber(item.chapter.chapterNumber),
                                )
                            } else {
                                item.chapter.name
                            },
                            date = null,
                            readProgress = null,
                            scanlator = item.chapter.scanlator,
                            read = item.chapter.read,
                            bookmark = item.chapter.bookmark,
                            hidden = true,
                            selected = item.id in selectedIds,
                            downloadIndicatorEnabled = false,
                            downloadStateProvider = { Download.State.NOT_DOWNLOADED },
                            downloadProgressProvider = { 0 },
                            chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.Disabled,
                            chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.Disabled,
                            onLongClick = { selection.toggle(rows, item, item.id !in selectedIds, true) },
                            onClick = { selection.toggle(rows, item, item.id !in selectedIds, false) },
                            onDownloadClick = null,
                            onChapterSwipe = {},
                        )
                    }
                }
            }
        }
    }
}
