package eu.kanade.presentation.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.time.Duration.Companion.seconds

// Yokai: SWIPE_THRESHOLD * 3 = 150px, SWIPE_VELOCITY_THRESHOLD = 100
private const val SWIPE_THRESHOLD = 150f
private const val SWIPE_VELOCITY_THRESHOLD = 100f

@Composable
fun LibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    pagedBrowsing: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getRowsForPagedBrowsing: () -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
) {
    val layoutDirection = LocalLayoutDirection.current
    val showingTabs =
        showPageTabs && categories.isNotEmpty() && (categories.size > 1 || (!categories.first().isSystemCategory))

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        val pagerState = rememberPagerState(currentPage) { categories.size }
        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(value = false) }

        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val showHopper by libraryPreferences.showCategoryHopper.collectAsState()

        if (showingTabs) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            Box(
                modifier = Modifier.padding(
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
            ) {
                LibraryTabs(
                    categories = categories,
                    pagerState = pagerState,
                    getItemCountForCategory = getItemCountForCategory,
                ) {
                    scope.launch { pagerState.animateScrollToPage(it) }
                }
            }
        }

        // Yokai-style rubber-band: tracks the elastic translationX applied
        // to the current page content during a horizontal drag gesture.
        val pageTranslationX = remember { Animatable(0f) }

        // Accumulated drag distance for the current gesture, matching
        // Yokai's startingX/e2.x pair.
        var startX by remember { mutableFloatStateOf(0f) }
        var startY by remember { mutableFloatStateOf(0f) }
        val lockedState = remember { mutableStateOf(false) }
        var locked by lockedState // horizontal axis locked (swipe wins)
        var cancelled by remember { mutableStateOf(false) } // vertical axis locked (scroll wins)

        // When a horizontal swipe is locked in, pre-consume ALL scroll so the
        // underlying grid/list can't scroll vertically at the same time.
        // Uses NestedScrollConnection instead of PointerEventPass.Initial so
        // the category hopper's own drag gestures are never intercepted.
        val swipeScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    return if (lockedState.value) available else Offset.Zero
                }
            }
        }

        val innerContentPadding = if (showingTabs) {
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
        } else {
            contentPadding
        }

        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
            indicatorPadding = innerContentPadding,
            onRefresh = {
                val started = onRefresh()
                if (!started) return@PullRefresh
                scope.launch {
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
        ) {
            LibraryPager(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(swipeScrollConnection)
                    .pointerInput(categories.size) {
                        var velocityX = 0f
                        var lastEventTime = 0L
                        var lastX = 0f

                        awaitPointerEventScope {
                            while (true) {
                                // Default (Main) pass — children get events too, so the
                                // category hopper's drag gestures still work. Scroll is
                                // blocked by swipeScrollConnection.onPreScroll instead.
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull() ?: continue

                                when {
                                    pointer.pressed && !pointer.previousPressed -> {
                                        startX = pointer.position.x
                                        startY = pointer.position.y
                                        lastX = pointer.position.x
                                        lastEventTime = System.currentTimeMillis()
                                        velocityX = 0f
                                        locked = false
                                        cancelled = false
                                    }
                                    pointer.pressed -> {
                                        val dx = pointer.position.x - lastX
                                        val now = System.currentTimeMillis()
                                        val dt = (now - lastEventTime).coerceAtLeast(1)
                                        velocityX = dx / dt * 1000f
                                        lastX = pointer.position.x
                                        lastEventTime = now

                                        val diffX = pointer.position.x - startX
                                        val diffY = pointer.position.y - startY

                                        if (!locked && !cancelled) {
                                            when {
                                                abs(diffX) > 50f -> locked = true  // swipe wins
                                                abs(diffY) > 50f -> cancelled = true // scroll wins
                                            }
                                        }

                                        if (locked && !cancelled) {
                                            val distance = startX - pointer.position.x
                                            val t = abs(distance / 50f).pow(1.7f) * -sign(distance / 50f)
                                            scope.launch { pageTranslationX.snapTo(t) }
                                        }
                                    }
                                    !pointer.pressed -> {
                                        val diffX = pointer.position.x - startX
                                        if (locked && !cancelled &&
                                            abs(diffX) >= SWIPE_THRESHOLD &&
                                            abs(velocityX) > SWIPE_VELOCITY_THRESHOLD &&
                                            sign(diffX) == sign(velocityX)
                                        ) {
                                            val page = pagerState.currentPage
                                            val next = if (diffX < 0) page + 1 else page - 1
                                            val target = next.coerceIn(0, categories.size - 1)
                                            if (target != page) {
                                                scope.launch {
                                                    pagerState.scrollToPage(target)
                                                    pageTranslationX.animateTo(
                                                        0f,
                                                        spring(
                                                            stiffness = Spring.StiffnessMedium,
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                        ),
                                                    )
                                                }
                                            } else {
                                                scope.launch {
                                                    pageTranslationX.animateTo(
                                                        0f,
                                                        spring(stiffness = Spring.StiffnessMedium),
                                                    )
                                                }
                                            }
                                        } else if (locked) {
                                            scope.launch {
                                                pageTranslationX.animateTo(
                                                    0f,
                                                    spring(stiffness = Spring.StiffnessMedium),
                                                )
                                            }
                                        }
                                        locked = false
                                        cancelled = false
                                    }
                                }
                            }
                        }
                    },
                state = pagerState,
                pageTranslationX = pageTranslationX,
                contentPadding = innerContentPadding,
                hasActiveFilters = hasActiveFilters,
                pagedBrowsing = pagedBrowsing,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getCategoryForPage = { page -> categories[page] },
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getRowsForPagedBrowsing = getRowsForPagedBrowsing,
                getItemsForCategory = getItemsForCategory,
                onClickManga = { category, manga ->
                    if (selection.isNotEmpty()) {
                        onToggleSelection(category, manga)
                    } else {
                        onClickManga(manga.manga.id)
                    }
                },
                onLongClickManga = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
                categories = categories,
                onSelectCategory = {
                    scope.launch { pagerState.animateScrollToPage(it) }
                },
                showHopper = showHopper,
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
