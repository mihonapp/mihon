package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import eu.kanade.presentation.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.manga.components.MangaActionRow
import eu.kanade.presentation.manga.components.MangaBottomActionMenu
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.MangaInfoBox
import eu.kanade.presentation.manga.components.MangaSmallAppBar
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
    val layoutDirection = LocalLayoutDirection.current
    val chapterListState = rememberLazyListState()

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
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
            .padding(insetPadding),
        topBar = {
            val firstVisibleItemIndex by remember {
                derivedStateOf { chapterListState.firstVisibleItemIndex }
            }
            val firstVisibleItemScrollOffset by remember {
                derivedStateOf { chapterListState.firstVisibleItemScrollOffset }
            }
            val animatedTitleAlpha by animateFloatAsState(
                if (firstVisibleItemIndex > 0) 1f else 0f,
            )
            val animatedBgAlpha by animateFloatAsState(
                if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0) 1f else 0f,
            )
            MangaSmallAppBar(
                title = state.manga.title,
                titleAlphaProvider = { animatedTitleAlpha },
                backgroundAlphaProvider = { animatedBgAlpha },
                incognitoMode = state.isIncognitoMode,
                downloadedOnlyMode = state.isDownloadedOnlyMode,
                onBackClicked = onBackClicked,
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
            SharedMangaBottomActionMenu(
                selected = selected,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                fillFraction = 1f,
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
        val noTopContentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding(),
        ) + WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
        val topPadding = contentPadding.calculateTopPadding()

        SwipeRefresh(
            state = rememberSwipeRefreshState(state.isRefreshingInfo || state.isRefreshingChapter),
            onRefresh = onRefresh,
            indicatorPadding = contentPadding,
            indicator = { s, trigger ->
                SwipeRefreshIndicator(
                    state = s,
                    refreshTriggerDistance = trigger,
                )
            },
        ) {
            VerticalFastScroller(
                listState = chapterListState,
                topContentPadding = topPadding,
                endContentPadding = noTopContentPadding.calculateEndPadding(layoutDirection),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    state = chapterListState,
                    contentPadding = noTopContentPadding,
                ) {
                    item(contentType = "info_box") {
                        MangaInfoBox(
                            windowWidthSizeClass = WindowWidthSizeClass.Compact,
                            appBarPadding = topPadding,
                            title = state.manga.title,
                            author = state.manga.author,
                            artist = state.manga.artist,
                            sourceName = remember { state.source.getNameForMangaInfo() },
                            isStubSource = remember { state.source is SourceManager.StubSource },
                            coverDataProvider = { state.manga },
                            status = state.manga.status,
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                    }

                    item(contentType = "action_row") {
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditCategory = onEditCategoryClicked,
                        )
                    }

                    item(contentType = "desc") {
                        ExpandableMangaDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            onTagClicked = onTagClicked,
                        )
                    }

                    item(contentType = "header") {
                        ChapterHeader(
                            chapterCount = chapters.size,
                            isChapterFiltered = state.manga.chaptersFiltered(),
                            onFilterButtonClicked = onFilterButtonClicked,
                        )
                    }

                    sharedChapterItems(
                        chapters = chapters,
                        state = state,
                        selected = selected,
                        selectedPositions = selectedPositions,
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter,
                    )
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
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

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
                    SharedMangaBottomActionMenu(
                        selected = selected,
                        onMultiBookmarkClicked = onMultiBookmarkClicked,
                        onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                        onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                        onDownloadChapter = onDownloadChapter,
                        onMultiDeleteClicked = onMultiDeleteClicked,
                        fillFraction = 0.5f,
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = withNavBarContentPadding.calculateBottomPadding()),
                ) {
                    MangaInfoBox(
                        windowWidthSizeClass = windowWidthSizeClass,
                        appBarPadding = contentPadding.calculateTopPadding(),
                        title = state.manga.title,
                        author = state.manga.author,
                        artist = state.manga.artist,
                        sourceName = remember { state.source.getNameForMangaInfo() },
                        isStubSource = remember { state.source is SourceManager.StubSource },
                        coverDataProvider = { state.manga },
                        status = state.manga.status,
                        onCoverClick = onCoverClicked,
                        doSearch = onSearch,
                    )
                    MangaActionRow(
                        favorite = state.manga.favorite,
                        trackingCount = state.trackingCount,
                        onAddToLibraryClicked = onAddToLibraryClicked,
                        onWebViewClicked = onWebViewClicked,
                        onTrackingClicked = onTrackingClicked,
                        onEditCategory = onEditCategoryClicked,
                    )
                    ExpandableMangaDescription(
                        defaultExpandState = true,
                        description = state.manga.description,
                        tagsProvider = { state.manga.genre },
                        onTagClicked = onTagClicked,
                    )
                }

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

                        sharedChapterItems(
                            chapters = chapters,
                            state = state,
                            selected = selected,
                            selectedPositions = selectedPositions,
                            onChapterClicked = onChapterClicked,
                            onDownloadChapter = onDownloadChapter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMangaBottomActionMenu(
    selected: SnapshotStateList<ChapterItem>,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    fillFraction: Float,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(fillFraction),
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
}

private fun LazyListScope.sharedChapterItems(
    chapters: List<ChapterItem>,
    state: MangaScreenState.Success,
    selected: SnapshotStateList<ChapterItem>,
    selectedPositions: Array<Int>,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
) {
    items(items = chapters) { chapterItem ->
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current

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
        val scanlator = remember(chapter.scanlator) { chapter.scanlator.takeIf { !it.isNullOrBlank() } }

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

private fun onChapterItemClick(
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
