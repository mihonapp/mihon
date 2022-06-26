package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarScrollState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga.Companion.CHAPTER_DISPLAY_NUMBER
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.SwipeRefreshIndicator
import eu.kanade.presentation.components.VerticalFastScroller
import eu.kanade.presentation.manga.components.ChapterHeader
import eu.kanade.presentation.manga.components.MangaBottomActionMenu
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.MangaInfoHeader
import eu.kanade.presentation.manga.components.MangaSmallAppBar
import eu.kanade.presentation.manga.components.MangaTopAppBar
import eu.kanade.presentation.util.ExitUntilCollapsedScrollBehavior
import eu.kanade.presentation.util.isScrolledToEnd
import eu.kanade.presentation.util.isScrollingUp
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.ui.manga.ChapterItem
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.lang.toRelativeString
import kotlinx.coroutines.runBlocking
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date

private val chapterDecimalFormat = DecimalFormat(
    "#.###",
    DecimalFormatSymbols()
        .apply { decimalSeparator = '.' },
)

@Composable
fun MangaScreen(
    state: MangaScreenState.Success,
    snackbarHostState: SnackbarHostState,
    windowWidthSizeClass: WindowWidthSizeClass,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagClicked: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
) {
    if (windowWidthSizeClass == WindowWidthSizeClass.Compact) {
        MangaScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            onBackClicked = onBackClicked,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onTrackingClicked = onTrackingClicked,
            onTagClicked = onTagClicked,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
        )
    } else {
        MangaScreenLargeImpl(
            state = state,
            windowWidthSizeClass = windowWidthSizeClass,
            snackbarHostState = snackbarHostState,
            onBackClicked = onBackClicked,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onTrackingClicked = onTrackingClicked,
            onTagClicked = onTagClicked,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
        )
    }
}

@Composable
private fun MangaScreenSmallImpl(
    state: MangaScreenState.Success,
    snackbarHostState: SnackbarHostState,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagClicked: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val scrollBehavior = ExitUntilCollapsedScrollBehavior(rememberTopAppBarScrollState(), decayAnimationSpec)
    val chapterListState = rememberLazyListState()
    SideEffect {
        if (chapterListState.firstVisibleItemIndex > 0 || chapterListState.firstVisibleItemScrollOffset > 0) {
            // Should go here after a configuration change
            // Safe to say that the app bar is fully scrolled
            scrollBehavior.state.offset = scrollBehavior.state.offsetLimit
        }
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val (topBarHeight, onTopBarHeightChanged) = remember { mutableStateOf(1) }
    SwipeRefresh(
        state = rememberSwipeRefreshState(state.isRefreshingInfo || state.isRefreshingChapter),
        onRefresh = onRefresh,
        indicatorPadding = PaddingValues(
            start = insetPadding.calculateStartPadding(layoutDirection),
            top = with(LocalDensity.current) { topBarHeight.toDp() },
            end = insetPadding.calculateEndPadding(layoutDirection),
        ),
        indicator = { s, trigger ->
            SwipeRefreshIndicator(
                state = s,
                refreshTriggerDistance = trigger,
            )
        },
    ) {
        val chapters = remember(state) { state.processedChapters.toList() }
        val selected = remember(chapters) { emptyList<ChapterItem>().toMutableStateList() }
        val selectedPositions = remember(chapters) { arrayOf(-1, -1) } // first and last selected index in list

        val internalOnBackPressed = {
            if (selected.isNotEmpty()) {
                selected.clear()
            } else {
                onBackClicked()
            }
        }
        BackHandler(onBack = internalOnBackPressed)

        Scaffold(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(insetPadding),
            topBar = {
                MangaTopAppBar(
                    modifier = Modifier
                        .scrollable(
                            state = rememberScrollableState {
                                var consumed = runBlocking { chapterListState.scrollBy(-it) } * -1
                                if (consumed == 0f) {
                                    // Pass scroll to app bar if we're on the top of the list
                                    val newOffset =
                                        (scrollBehavior.state.offset + it).coerceIn(scrollBehavior.state.offsetLimit, 0f)
                                    consumed = newOffset - scrollBehavior.state.offset
                                    scrollBehavior.state.offset = newOffset
                                }
                                consumed
                            },
                            orientation = Orientation.Vertical,
                            interactionSource = chapterListState.interactionSource as MutableInteractionSource,
                        ),
                    title = state.manga.title,
                    author = state.manga.author,
                    artist = state.manga.artist,
                    description = state.manga.description,
                    tagsProvider = { state.manga.genre },
                    coverDataProvider = { state.manga },
                    sourceName = remember { state.source.getNameForMangaInfo() },
                    isStubSource = remember { state.source is SourceManager.StubSource },
                    favorite = state.manga.favorite,
                    status = state.manga.status,
                    trackingCount = state.trackingCount,
                    chapterCount = chapters.size,
                    chapterFiltered = state.manga.chaptersFiltered(),
                    incognitoMode = state.isIncognitoMode,
                    downloadedOnlyMode = state.isDownloadedOnlyMode,
                    fromSource = state.isFromSource,
                    onBackClicked = internalOnBackPressed,
                    onCoverClick = onCoverClicked,
                    onTagClicked = onTagClicked,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onFilterButtonClicked = onFilterButtonClicked,
                    onShareClicked = onShareClicked,
                    onDownloadClicked = onDownloadActionClicked,
                    onEditCategoryClicked = onEditCategoryClicked,
                    onMigrateClicked = onMigrateClicked,
                    doGlobalSearch = onSearch,
                    scrollBehavior = scrollBehavior,
                    actionModeCounter = selected.size,
                    onSelectAll = {
                        selected.clear()
                        selected.addAll(chapters)
                    },
                    onInvertSelection = {
                        val toSelect = chapters - selected
                        selected.clear()
                        selected.addAll(toSelect)
                    },
                    onSmallAppBarHeightChanged = onTopBarHeightChanged,
                )
            },
            bottomBar = {
                MangaBottomActionMenu(
                    visible = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    onBookmarkClicked = {
                        onMultiBookmarkClicked.invoke(selected.map { it.chapter }, true)
                        selected.clear()
                    }.takeIf { selected.any { !it.chapter.bookmark } },
                    onRemoveBookmarkClicked = {
                        onMultiBookmarkClicked.invoke(selected.map { it.chapter }, false)
                        selected.clear()
                    }.takeIf { selected.all { it.chapter.bookmark } },
                    onMarkAsReadClicked = {
                        onMultiMarkAsReadClicked(selected.map { it.chapter }, true)
                        selected.clear()
                    }.takeIf { selected.any { !it.chapter.read } },
                    onMarkAsUnreadClicked = {
                        onMultiMarkAsReadClicked(selected.map { it.chapter }, false)
                        selected.clear()
                    }.takeIf { selected.any { it.chapter.read } },
                    onMarkPreviousAsReadClicked = {
                        onMarkPreviousAsReadClicked(selected[0].chapter)
                        selected.clear()
                    }.takeIf { selected.size == 1 },
                    onDownloadClicked = {
                        onDownloadChapter!!(selected.toList(), ChapterDownloadAction.START)
                        selected.clear()
                    }.takeIf {
                        onDownloadChapter != null && selected.any { it.downloadState != Download.State.DOWNLOADED }
                    },
                    onDeleteClicked = {
                        onMultiDeleteClicked(selected.map { it.chapter })
                        selected.clear()
                    }.takeIf {
                        onDownloadChapter != null && selected.any { it.downloadState == Download.State.DOWNLOADED }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = chapters.any { !it.chapter.read } && selected.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val id = if (chapters.any { it.chapter.read }) {
                                R.string.action_resume
                            } else {
                                R.string.action_start
                            }
                            Text(text = stringResource(id))
                        },
                        icon = { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = onContinueReading,
                        expanded = chapterListState.isScrollingUp() || chapterListState.isScrolledToEnd(),
                        modifier = Modifier
                            .padding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()),
                    )
                }
            },
        ) { contentPadding ->
            val withNavBarContentPadding = contentPadding +
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
            VerticalFastScroller(
                listState = chapterListState,
                topContentPadding = withNavBarContentPadding.calculateTopPadding(),
                endContentPadding = withNavBarContentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    state = chapterListState,
                    contentPadding = withNavBarContentPadding,
                ) {
                    items(items = chapters) { chapterItem ->
                        val (chapter, downloadState, downloadProgress) = chapterItem
                        val chapterTitle = if (state.manga.displayMode == CHAPTER_DISPLAY_NUMBER) {
                            stringResource(
                                id = R.string.display_mode_chapter,
                                chapterDecimalFormat.format(chapter.chapterNumber.toDouble()),
                            )
                        } else {
                            chapter.name
                        }
                        val date = remember(chapter.dateUpload) {
                            chapter.dateUpload
                                .takeIf { it > 0 }
                                ?.let { Date(it).toRelativeString(context, state.dateRelativeTime, state.dateFormat) }
                        }
                        val lastPageRead = remember(chapter.lastPageRead) {
                            chapter.lastPageRead.takeIf { !chapter.read && it > 0 }
                        }
                        val scanlator = remember(chapter.scanlator) { chapter.scanlator.takeIf { !it.isNullOrBlank() } }

                        MangaChapterListItem(
                            title = chapterTitle,
                            date = date,
                            readProgress = lastPageRead?.let { stringResource(R.string.chapter_progress, it + 1) },
                            scanlator = scanlator,
                            read = chapter.read,
                            bookmark = chapter.bookmark,
                            selected = selected.contains(chapterItem),
                            downloadState = downloadState,
                            downloadProgress = downloadProgress,
                            onLongClick = {
                                val dispatched = onChapterItemLongClick(
                                    chapterItem = chapterItem,
                                    selected = selected,
                                    chapters = chapters,
                                    selectedPositions = selectedPositions,
                                )
                                if (dispatched) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClick = {
                                onChapterItemClick(
                                    chapterItem = chapterItem,
                                    selected = selected,
                                    chapters = chapters,
                                    selectedPositions = selectedPositions,
                                    onChapterClicked = onChapterClicked,
                                )
                            },
                            onDownloadClick = if (onDownloadChapter != null) {
                                { onDownloadChapter(listOf(chapterItem), it) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MangaScreenLargeImpl(
    state: MangaScreenState.Success,
    windowWidthSizeClass: WindowWidthSizeClass,
    snackbarHostState: SnackbarHostState,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagClicked: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val (topBarHeight, onTopBarHeightChanged) = remember { mutableStateOf(0) }
    SwipeRefresh(
        state = rememberSwipeRefreshState(state.isRefreshingInfo || state.isRefreshingChapter),
        onRefresh = onRefresh,
        indicatorPadding = PaddingValues(
            start = insetPadding.calculateStartPadding(layoutDirection),
            top = with(density) { topBarHeight.toDp() },
            end = insetPadding.calculateEndPadding(layoutDirection),
        ),
        clipIndicatorToPadding = true,
        indicator = { s, trigger ->
            SwipeRefreshIndicator(
                state = s,
                refreshTriggerDistance = trigger,
            )
        },
    ) {
        val chapterListState = rememberLazyListState()
        val chapters = remember(state) { state.processedChapters.toList() }
        val selected = remember(chapters) { emptyList<ChapterItem>().toMutableStateList() }
        val selectedPositions = remember(chapters) { arrayOf(-1, -1) } // first and last selected index in list

        val internalOnBackPressed = {
            if (selected.isNotEmpty()) {
                selected.clear()
            } else {
                onBackClicked()
            }
        }
        BackHandler(onBack = internalOnBackPressed)

        Scaffold(
            modifier = Modifier.padding(insetPadding),
            topBar = {
                MangaSmallAppBar(
                    modifier = Modifier.onSizeChanged { onTopBarHeightChanged(it.height) },
                    title = state.manga.title,
                    titleAlphaProvider = { if (selected.isEmpty()) 0f else 1f },
                    backgroundAlphaProvider = { 1f },
                    incognitoMode = state.isIncognitoMode,
                    downloadedOnlyMode = state.isDownloadedOnlyMode,
                    onBackClicked = internalOnBackPressed,
                    onShareClicked = onShareClicked,
                    onDownloadClicked = onDownloadActionClicked,
                    onEditCategoryClicked = onEditCategoryClicked,
                    onMigrateClicked = onMigrateClicked,
                    actionModeCounter = selected.size,
                    onSelectAll = {
                        selected.clear()
                        selected.addAll(chapters)
                    },
                    onInvertSelection = {
                        val toSelect = chapters - selected
                        selected.clear()
                        selected.addAll(toSelect)
                    },
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    MangaBottomActionMenu(
                        visible = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(0.5f),
                        onBookmarkClicked = {
                            onMultiBookmarkClicked.invoke(selected.map { it.chapter }, true)
                            selected.clear()
                        }.takeIf { selected.any { !it.chapter.bookmark } },
                        onRemoveBookmarkClicked = {
                            onMultiBookmarkClicked.invoke(selected.map { it.chapter }, false)
                            selected.clear()
                        }.takeIf { selected.all { it.chapter.bookmark } },
                        onMarkAsReadClicked = {
                            onMultiMarkAsReadClicked(selected.map { it.chapter }, true)
                            selected.clear()
                        }.takeIf { selected.any { !it.chapter.read } },
                        onMarkAsUnreadClicked = {
                            onMultiMarkAsReadClicked(selected.map { it.chapter }, false)
                            selected.clear()
                        }.takeIf { selected.any { it.chapter.read } },
                        onMarkPreviousAsReadClicked = {
                            onMarkPreviousAsReadClicked(selected[0].chapter)
                            selected.clear()
                        }.takeIf { selected.size == 1 },
                        onDownloadClicked = {
                            onDownloadChapter!!(selected, ChapterDownloadAction.START)
                            selected.clear()
                        }.takeIf {
                            onDownloadChapter != null && selected.any { it.downloadState != Download.State.DOWNLOADED }
                        },
                        onDeleteClicked = {
                            onMultiDeleteClicked(selected.map { it.chapter })
                            selected.clear()
                        }.takeIf {
                            onDownloadChapter != null && selected.any { it.downloadState == Download.State.DOWNLOADED }
                        },
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = chapters.any { !it.chapter.read } && selected.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val id = if (chapters.any { it.chapter.read }) {
                                R.string.action_resume
                            } else {
                                R.string.action_start
                            }
                            Text(text = stringResource(id))
                        },
                        icon = { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = onContinueReading,
                        expanded = chapterListState.isScrollingUp() || chapterListState.isScrolledToEnd(),
                        modifier = Modifier
                            .padding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()),
                    )
                }
            },
        ) { contentPadding ->
            Row {
                val withNavBarContentPadding = contentPadding +
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
                MangaInfoHeader(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = withNavBarContentPadding.calculateBottomPadding()),
                    windowWidthSizeClass = WindowWidthSizeClass.Expanded,
                    appBarPadding = contentPadding.calculateTopPadding(),
                    title = state.manga.title,
                    author = state.manga.author,
                    artist = state.manga.artist,
                    description = state.manga.description,
                    tagsProvider = { state.manga.genre },
                    sourceName = remember { state.source.getNameForMangaInfo() },
                    isStubSource = remember { state.source is SourceManager.StubSource },
                    coverDataProvider = { state.manga },
                    favorite = state.manga.favorite,
                    status = state.manga.status,
                    trackingCount = state.trackingCount,
                    fromSource = state.isFromSource,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onTagClicked = onTagClicked,
                    onEditCategory = onEditCategoryClicked,
                    onCoverClick = onCoverClicked,
                    doSearch = onSearch,
                )

                val chaptersWeight = if (windowWidthSizeClass == WindowWidthSizeClass.Medium) 1f else 2f
                VerticalFastScroller(
                    listState = chapterListState,
                    modifier = Modifier.weight(chaptersWeight),
                    topContentPadding = withNavBarContentPadding.calculateTopPadding(),
                    endContentPadding = withNavBarContentPadding.calculateEndPadding(layoutDirection),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        state = chapterListState,
                        contentPadding = withNavBarContentPadding,
                    ) {
                        item(contentType = "header") {
                            ChapterHeader(
                                chapterCount = chapters.size,
                                isChapterFiltered = state.manga.chaptersFiltered(),
                                onFilterButtonClicked = onFilterButtonClicked,
                            )
                        }

                        items(items = chapters) { chapterItem ->
                            val (chapter, downloadState, downloadProgress) = chapterItem
                            val chapterTitle = remember(state.manga.displayMode, chapter.chapterNumber, chapter.name) {
                                if (state.manga.displayMode == CHAPTER_DISPLAY_NUMBER) {
                                    chapterDecimalFormat.format(chapter.chapterNumber.toDouble())
                                } else {
                                    chapter.name
                                }
                            }
                            val date = remember(chapter.dateUpload) {
                                chapter.dateUpload
                                    .takeIf { it > 0 }
                                    ?.let {
                                        Date(it).toRelativeString(
                                            context,
                                            state.dateRelativeTime,
                                            state.dateFormat,
                                        )
                                    }
                            }
                            val lastPageRead = remember(chapter.lastPageRead) {
                                chapter.lastPageRead.takeIf { !chapter.read && it > 0 }
                            }
                            val scanlator =
                                remember(chapter.scanlator) { chapter.scanlator.takeIf { !it.isNullOrBlank() } }

                            MangaChapterListItem(
                                title = chapterTitle,
                                date = date,
                                readProgress = lastPageRead?.let {
                                    stringResource(
                                        id = R.string.chapter_progress,
                                        it + 1,
                                    )
                                },
                                scanlator = scanlator,
                                read = chapter.read,
                                bookmark = chapter.bookmark,
                                selected = selected.contains(chapterItem),
                                downloadState = downloadState,
                                downloadProgress = downloadProgress,
                                onLongClick = {
                                    val dispatched = onChapterItemLongClick(
                                        chapterItem = chapterItem,
                                        selected = selected,
                                        chapters = chapters,
                                        selectedPositions = selectedPositions,
                                    )
                                    if (dispatched) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onClick = {
                                    onChapterItemClick(
                                        chapterItem = chapterItem,
                                        selected = selected,
                                        chapters = chapters,
                                        selectedPositions = selectedPositions,
                                        onChapterClicked = onChapterClicked,
                                    )
                                },
                                onDownloadClick = if (onDownloadChapter != null) {
                                    { onDownloadChapter(listOf(chapterItem), it) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun onChapterItemLongClick(
    chapterItem: ChapterItem,
    selected: MutableList<ChapterItem>,
    chapters: List<ChapterItem>,
    selectedPositions: Array<Int>,
): Boolean {
    if (!selected.contains(chapterItem)) {
        val selectedIndex = chapters.indexOf(chapterItem)
        if (selected.isEmpty()) {
            selected.add(chapterItem)
            selectedPositions[0] = selectedIndex
            selectedPositions[1] = selectedIndex
            return true
        }

        // Try to select the items in-between when possible
        val range: IntRange
        if (selectedIndex < selectedPositions[0]) {
            range = selectedIndex until selectedPositions[0]
            selectedPositions[0] = selectedIndex
        } else if (selectedIndex > selectedPositions[1]) {
            range = (selectedPositions[1] + 1)..selectedIndex
            selectedPositions[1] = selectedIndex
        } else {
            // Just select itself
            range = selectedIndex..selectedIndex
        }

        range.forEach {
            val toAdd = chapters[it]
            if (!selected.contains(toAdd)) {
                selected.add(toAdd)
            }
        }
        return true
    }
    return false
}

fun onChapterItemClick(
    chapterItem: ChapterItem,
    selected: MutableList<ChapterItem>,
    chapters: List<ChapterItem>,
    selectedPositions: Array<Int>,
    onChapterClicked: (Chapter) -> Unit,
) {
    val selectedIndex = chapters.indexOf(chapterItem)
    when {
        selected.contains(chapterItem) -> {
            val removedIndex = chapters.indexOf(chapterItem)
            selected.remove(chapterItem)

            if (removedIndex == selectedPositions[0]) {
                selectedPositions[0] = chapters.indexOfFirst { selected.contains(it) }
            } else if (removedIndex == selectedPositions[1]) {
                selectedPositions[1] = chapters.indexOfLast { selected.contains(it) }
            }
        }
        selected.isNotEmpty() -> {
            if (selectedIndex < selectedPositions[0]) {
                selectedPositions[0] = selectedIndex
            } else if (selectedIndex > selectedPositions[1]) {
                selectedPositions[1] = selectedIndex
            }
            selected.add(chapterItem)
        }
        else -> onChapterClicked(chapterItem.chapter)
    }
}
