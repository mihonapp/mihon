package mihon.desktop.ui.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.image.LocalCustomCoverManager
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.input.ClickRegionPolicy
import mihon.desktop.reader.input.InputPoint
import mihon.desktop.reader.input.ReaderInputCommand
import mihon.desktop.reader.input.ReaderInputContext
import mihon.desktop.reader.input.ReaderInputKey
import mihon.desktop.reader.input.ReaderInputMapper
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderSessionError
import mihon.reader.session.ReaderState
import mihon.reader.source.ReaderFailure
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ReaderScreen(
    session: ReaderSession,
    title: String,
    chapterTitle: String,
    settingsStore: DesktopReaderSettingsStore?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    onBorderless: () -> Unit = {},
    onEscape: () -> Boolean = { true },
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onChapterSelected: ((Long) -> Unit)? = null,
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    onRetryChapter: suspend () -> Unit = {},
    foreground: Boolean = true,
    debugEnabled: Boolean = System.getenv("MIHON_W_READER_DEBUG") == "1",
    doubleTapZoom: Float = ReaderGesturePolicy.DEFAULT_DOUBLE_TAP_ZOOM,
    knownPageSizes: Map<PageId, PageSize> = emptyMap(),
    chapterBookmarked: Boolean = false,
    onChapterBookmarkChanged: (Boolean) -> Unit = {},
    bookmarkStore: ReaderChapterBookmarkStore? = null,
    chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
    previousChapter: ReaderChapterTransitionChapter? = null,
    nextChapter: ReaderChapterTransitionChapter? = null,
    currentChapter: ReaderChapterTransitionChapter? = null,
    currentChapterDownloaded: Boolean = false,
    initialTransitionDirection: ReaderChapterTransitionDirection? = null,
    onChapterTransitionContinue: ((ReaderChapterTransitionDirection) -> Unit)? = null,
    pageActionHandler: ReaderPageActionHandler? = null,
    mangaId: Long? = null,
    pageUrlResolver: (PageDescriptor) -> String? = { null },
    pageActionTargetProvider: (PageDescriptor, Int) -> ReaderPageActionTarget = { page, index ->
        ReaderPageActionTarget(
            page = page,
            pageIndex = index,
            pageUrl = pageUrlResolver(page),
            mangaId = mangaId,
        )
    },
    pageImageStore: ReaderPageImageStore? = null,
    pageContent: ReaderPageContent = { _, pageIndex, contentModifier ->
        ReaderPagePlaceholder(pageIndex, contentModifier)
    },
) {
    val state by session.state.collectAsState()
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var settings by remember(settingsStore) {
        mutableStateOf(settingsStore?.load() ?: state.toDesktopSettings())
    }
    var overlayVisibility by remember { mutableStateOf(ReaderOverlayVisibilityState()) }
    var hideGeneration by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showShortcuts by remember { mutableStateOf(false) }
    var closing by remember(session) { mutableStateOf(false) }
    var pressChromeTarget by remember { mutableStateOf<Boolean?>(null) }
    val panAccumulator = remember { ReaderPanAccumulator() }
    val clickPolicy = remember(settings.clickRegions) { ClickRegionPolicy.from(settings.clickRegions) }
    val inputMapper = remember { ReaderInputMapper() }
    val focusRequester = remember { FocusRequester() }

    // Intrinsic sizes observed by DecodedReaderPage plus any sizes supplied directly by callers/tests.
    val decodedPageSizes = remember(session) { mutableStateMapOf<PageId, PageSize>() }
    val pageSizeSink = remember(session, decodedPageSizes) {
        ReaderPageSizeSink { pageId, size -> decodedPageSizes[pageId] = size }
    }
    val effectivePageSizes: Map<PageId, PageSize> = knownPageSizes + decodedPageSizes
    val sizeChapterId = remember(session) { mutableStateOf<Long?>(null) }
    val imageStore = remember(session, pageImageStore) { pageImageStore ?: ReaderPageImageStore() }
    val customCoverManager = LocalCustomCoverManager.current
    val effectivePageActionHandler = remember(pageActionHandler, imageStore, customCoverManager) {
        pageActionHandler ?: defaultReaderPageActionHandler(imageStore, customCoverManager)
    }
    val effectiveBookmarkStore = remember(bookmarkStore, settingsStore) {
        bookmarkStore ?: settingsStore?.bookmarkStore()
    }
    var pageActionsTarget by remember(session) { mutableStateOf<ReaderPageActionTarget?>(null) }
    var pageActionMessage by remember { mutableStateOf<String?>(null) }
    var chapterTransition by remember(session) { mutableStateOf<ReaderChapterTransition?>(null) }
    var pendingExternalChapterDirection by remember(session) {
        mutableStateOf<ReaderChapterTransitionDirection?>(null)
    }
    var pendingChapterDirection by remember(session) { mutableStateOf<ReaderChapterTransitionDirection?>(null) }
    var observedChapterId by remember(session) { mutableStateOf(state.chapterId) }
    var bookmarked by remember(session, state.chapterId, effectiveBookmarkStore) {
        mutableStateOf(state.chapterId?.let { effectiveBookmarkStore?.isBookmarked(it) } ?: chapterBookmarked)
    }

    fun markReadingInput() {
        overlayVisibility = reduceReaderOverlayVisibility(overlayVisibility, ReaderOverlayEvent.ReadingInput)
        hideGeneration++
    }

    fun applySettings(updated: DesktopReaderSettings) {
        settings = updated
        settingsStore?.save(updated)
        session.dispatch(ReaderAction.ChangeMode(updated.mode))
        session.dispatch(ReaderAction.SetScaleMode(updated.scaleMode))
        session.dispatch(ReaderAction.SetCoverOffset(updated.coverOffset))
    }

    fun transitionChapterInfo(chapterId: Long? = state.chapterId): ReaderChapterTransitionChapter {
        val catalogChapter = chapterId?.let { id -> chapterCatalog.firstOrNull { it.id == id } }
        if (catalogChapter != null) return catalogChapter
        val explicit = currentChapter
        return when {
            explicit != null && (chapterId == null || explicit.id == chapterId) -> explicit
            explicit != null -> explicit.copy(id = chapterId)
            else -> ReaderChapterTransitionChapter(
                id = chapterId,
                title = chapterTitle,
                downloaded = currentChapterDownloaded,
            )
        }
    }

    fun buildChapterTransition(
        direction: ReaderChapterTransitionDirection,
        chapterId: Long? = state.chapterId,
    ): ReaderChapterTransition {
        val current = transitionChapterInfo(chapterId)
        val currentState = session.state.value
        val previousFallback = previousChapter ?: if (currentState.hasPreviousChapter) {
            ReaderChapterTransitionChapter(title = strings.readerPreviousChapter)
        } else {
            null
        }
        val nextFallback = nextChapter ?: if (currentState.hasNextChapter) {
            ReaderChapterTransitionChapter(title = strings.readerNextChapter)
        } else {
            null
        }
        return readerChapterTransition(
            direction = direction,
            current = current,
            chapters = chapterCatalog,
            previous = previousFallback,
            next = nextFallback,
            settings = settings,
        )
    }

    fun closeAndThen(afterClose: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { session.closeAndFlush() } }
            afterClose()
        }
    }

    fun navigateExternalChapter(direction: ReaderChapterTransitionDirection, targetChapterId: Long? = null) {
        closeAndThen {
            if (targetChapterId != null && onChapterSelected != null) {
                onChapterSelected(targetChapterId)
            } else {
                when (direction) {
                    ReaderChapterTransitionDirection.PREVIOUS -> onPreviousChapter()
                    ReaderChapterTransitionDirection.NEXT -> onNextChapter()
                }
            }
        }
    }

    fun openPageActions() {
        val current = session.state.value
        val page = current.pages.getOrNull(current.selectedIndex) ?: return
        pageActionsTarget = pageActionTargetProvider(page, current.selectedIndex)
    }

    fun runPageAction(
        target: ReaderPageActionTarget,
        block: suspend (ReaderPageActionHandler, ReaderPageActionTarget) -> Boolean,
    ) {
        pageActionsTarget = null
        scope.launch {
            val success = runCatching { block(effectivePageActionHandler, target) }.getOrDefault(false)
            if (!success) pageActionMessage = "Unable to complete page action"
        }
    }

    fun toggleChapterBookmark() {
        val chapterId = session.state.value.chapterId ?: return
        val updated = !bookmarked
        bookmarked = updated
        effectiveBookmarkStore?.setBookmarked(chapterId, updated)
        onChapterBookmarkChanged(updated)
    }

    fun dispatchCore(action: ReaderAction) {
        val current = session.state.value
        val boundaryDirection = when {
            action == ReaderAction.Next && current.selectedIndex == current.pages.lastIndex -> {
                ReaderChapterTransitionDirection.NEXT
            }
            action == ReaderAction.Previous && current.selectedIndex == 0 -> {
                ReaderChapterTransitionDirection.PREVIOUS
            }
            else -> null
        }
        if (boundaryDirection != null) {
            val hasAdjacentChapter = when (boundaryDirection) {
                ReaderChapterTransitionDirection.PREVIOUS -> current.hasPreviousChapter || hasPreviousChapter
                ReaderChapterTransitionDirection.NEXT -> current.hasNextChapter || hasNextChapter
            }
            if (settings.alwaysShowChapterTransition) {
                if (hasAdjacentChapter) pendingExternalChapterDirection = boundaryDirection
                chapterTransition = buildChapterTransition(boundaryDirection)
                if (!hasAdjacentChapter) session.dispatch(action)
            } else if (hasAdjacentChapter) {
                navigateExternalChapter(boundaryDirection)
            } else {
                // Preserve the core no-op boundary action for input/progress observers.
                session.dispatch(action)
            }
            return
        }
        session.dispatch(action)
    }

    fun handlePress(normalizedX: Float) {
        val action = clickPolicy.actionAt(normalizedX)
        pressChromeTarget = if (action == ReaderClickAction.TOGGLE_CHROME) !overlayVisibility.chromeVisible else null
        markReadingInput()
    }

    fun handleClickAction(action: ReaderClickAction, pointer: Boolean = false) {
        when (action) {
            ReaderClickAction.PREVIOUS -> {
                dispatchCore(ReaderAction.Previous)
                if (!pointer) markReadingInput()
            }
            ReaderClickAction.NEXT -> {
                dispatchCore(ReaderAction.Next)
                if (!pointer) markReadingInput()
            }
            ReaderClickAction.TOGGLE_CHROME -> {
                val visible = if (pointer) {
                    pressChromeTarget ?: !overlayVisibility.chromeVisible
                } else {
                    !overlayVisibility.chromeVisible
                }
                overlayVisibility = overlayVisibility.copy(chromeVisible = visible, cursorVisible = true)
                if (pointer) pressChromeTarget = null
            }
            ReaderClickAction.NONE -> if (!pointer) markReadingInput()
        }
    }

    fun panBoundsFor(current: ReaderState, viewport: ReaderViewport, zoom: Float): ReaderPanBounds {
        val webtoonMaxWidthPixels = with(density) { settings.webtoonMaxWidth.dp.roundToPx() }
        val selectedPageSize = current.pages.getOrNull(current.selectedIndex)?.let { effectivePageSizes[it.id] }
        return ReaderGesturePolicy.panBounds(
            state = current,
            viewport = viewport,
            zoom = zoom,
            webtoonMaxWidthPixels = webtoonMaxWidthPixels,
            webtoonSidePaddingPercent = settings.webtoonSidePadding,
            pageSize = selectedPageSize,
        )
    }

    fun handlePan(delta: ReaderPan, viewport: ReaderViewport) {
        val current = session.state.value
        if (current.pages.isEmpty()) return
        val effectiveDelta = if (current.mode == ReadingMode.VERTICAL || current.mode == ReadingMode.WEBTOON) {
            ReaderPan(delta.x, 0f)
        } else {
            delta
        }
        if (effectiveDelta.x == 0f && effectiveDelta.y == 0f) return
        val base = panAccumulator.value ?: current.pan
        val target = panBoundsFor(current, viewport, current.zoom).panBy(base, effectiveDelta)
        if (target == base) return
        panAccumulator.value = target
        session.dispatch(ReaderAction.SetPan(target))
        markReadingInput()
    }

    fun applyZoom(command: ReaderInputCommand.ZoomBy, viewport: ReaderViewport?) {
        val current = session.state.value
        val targetZoom = if (command.factor == 0f) {
            ReaderGesturePolicy.FIT_ZOOM
        } else {
            ReaderLayout.clampZoom(current.zoom * command.factor)
        }
        session.dispatch(ReaderAction.SetZoom(targetZoom))
        if (command.factor == 0f) {
            panAccumulator.value = ReaderPan(0f, 0f)
            session.dispatch(ReaderAction.SetPan(ReaderPan(0f, 0f)))
        } else if (viewport != null) {
            val base = panAccumulator.value ?: current.pan
            val targetPan = panBoundsFor(current, viewport, targetZoom)
                .clamp(ReaderGesturePolicy.scalePan(base, current.zoom, targetZoom))
            panAccumulator.value = targetPan
            session.dispatch(ReaderAction.SetPan(targetPan))
        }
        markReadingInput()
    }

    fun handleZoomBy(factor: Float, centroid: InputPoint, viewport: ReaderViewport) {
        val command = inputMapper.mapPinch(factor, centroid).action as? ReaderInputCommand.ZoomBy ?: return
        applyZoom(command, viewport)
    }

    fun handleInput(command: ReaderInputCommand) {
        when (command) {
            is ReaderInputCommand.Core -> {
                dispatchCore(command.action)
                markReadingInput()
            }
            is ReaderInputCommand.ZoomBy -> applyZoom(command, session.state.value.viewport)
            ReaderInputCommand.PageActions -> openPageActions()
            ReaderInputCommand.Fullscreen -> onFullscreen()
            ReaderInputCommand.Borderless -> onBorderless()
            ReaderInputCommand.Escape -> {
                if (onEscape()) closeAndThen(onBack)
            }
        }
    }

    fun handleWheel(
        deltaPixels: Float,
        ctrl: Boolean,
        centroid: InputPoint,
        viewport: ReaderViewport,
    ) {
        val current = session.state.value
        val continuous = current.mode == ReadingMode.VERTICAL || current.mode == ReadingMode.WEBTOON
        if (!ctrl && continuous) return
        val result = inputMapper.mapWheel(
            deltaPixels = deltaPixels,
            context = ReaderInputContext(
                mode = current.mode,
                wheelBehavior = settings.wheelBehavior,
                pageCount = current.pages.size.coerceAtLeast(1),
                anchor = current.viewportAnchor,
            ),
            ctrl = ctrl,
            centroid = centroid,
        )
        when (val command = result.action) {
            null -> Unit
            is ReaderInputCommand.ZoomBy -> applyZoom(command, viewport)
            else -> handleInput(command)
        }
    }

    fun handleDoubleTap(viewport: ReaderViewport) {
        val current = session.state.value
        val targetZoom = ReaderGesturePolicy.doubleTapZoom(current.zoom, doubleTapZoom)
        panAccumulator.value = ReaderPan(0f, 0f)
        session.dispatch(ReaderAction.SetZoom(targetZoom))
        session.dispatch(ReaderAction.SetPan(ReaderPan(0f, 0f)))
        markReadingInput()
    }

    fun handleTap(normalizedX: Float) {
        handleClickAction(clickPolicy.actionAt(normalizedX), pointer = true)
    }

    LaunchedEffect(hideGeneration) {
        if (hideGeneration > 0) {
            delay(CHROME_HIDE_DELAY_MILLIS)
            overlayVisibility = reduceReaderOverlayVisibility(overlayVisibility, ReaderOverlayEvent.IdleTimeout)
        }
    }
    LaunchedEffect(session, foreground) {
        session.dispatch(ReaderAction.SetForeground(foreground))
    }
    LaunchedEffect(session, state.chapterId) {
        if (state.chapterId != null) {
            session.dispatch(ReaderAction.SetPan(ReaderPan(0f, 0f)))
        }
    }
    LaunchedEffect(session, state.chapterId) {
        val chapterId = state.chapterId ?: return@LaunchedEffect
        val previous = sizeChapterId.value
        if (previous != null && previous != chapterId) {
            // Drop only the previous chapter; a new page may already have been promoted by its sink.
            val currentChapterKey = chapterId.toString()
            decodedPageSizes.keys
                .filter { it.chapterId != currentChapterKey }
                .forEach { decodedPageSizes.remove(it) }
        }
        sizeChapterId.value = chapterId
    }
    LaunchedEffect(state.selectedIndex, state.chapterId) {
        panAccumulator.value = null
    }
    LaunchedEffect(state.chapterId, chapterBookmarked, effectiveBookmarkStore) {
        bookmarked = state.chapterId?.let { effectiveBookmarkStore?.isBookmarked(it) } ?: chapterBookmarked
    }
    LaunchedEffect(session, state.chapterId) {
        val chapterId = state.chapterId
        val previous = observedChapterId
        if (chapterId != null && previous != null && chapterId != previous) {
            val direction = pendingChapterDirection ?: ReaderChapterTransitionDirection.NEXT
            if (settings.alwaysShowChapterTransition) {
                chapterTransition = buildChapterTransition(direction, chapterId)
            }
        }
        observedChapterId = chapterId
        pendingChapterDirection = null
    }
    LaunchedEffect(session, state.chapterId, initialTransitionDirection) {
        if (initialTransitionDirection != null && settings.alwaysShowChapterTransition && chapterTransition == null) {
            chapterTransition = buildChapterTransition(initialTransitionDirection, state.chapterId)
        }
    }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    DisposableEffect(session) {
        session.dispatch(ReaderAction.SetContentVisible(true))
        onDispose { session.dispatch(ReaderAction.SetContentVisible(false)) }
    }

    val ready = state.loadState is ReaderLoadState.Ready
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                if (!ready) return@onPointerEvent
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val deltaPixels = change.scrollDelta.y
                if (deltaPixels == 0f) return@onPointerEvent
                val viewport = state.viewport ?: return@onPointerEvent
                val centroid = InputPoint(
                    x = (change.position.x / viewport.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    y = (change.position.y / viewport.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                )
                val ctrl = event.keyboardModifiers.isPointerCtrlPressed
                handleWheel(deltaPixels, ctrl, centroid, viewport)
                val continuous = state.mode == ReadingMode.VERTICAL || state.mode == ReadingMode.WEBTOON
                if (ctrl || !continuous) {
                    event.changes.forEach { it.consume() }
                }
            }
            .pointerHoverIcon(rememberReaderPointerIcon(overlayVisibility.cursorVisible))
            .testTag("reader-screen")
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.key == Key.Escape) {
                    val dismissedOverlay = when {
                        pageActionsTarget != null -> true.also { pageActionsTarget = null }
                        showSettings -> true.also { showSettings = false }
                        showShortcuts -> true.also { showShortcuts = false }
                        chapterTransition != null -> true.also { chapterTransition = null }
                        else -> false
                    }
                    if (dismissedOverlay) return@onKeyEvent true
                }
                if (event.key == Key.F1 || event.key == Key.Slash || event.key == Key.Help) {
                    showShortcuts = true
                    return@onKeyEvent true
                }
                val key = event.toReaderInputKey() ?: return@onKeyEvent false
                val result = inputMapper.mapKey(
                    key,
                    ReaderInputContext(
                        mode = state.mode,
                        wheelBehavior = settings.wheelBehavior,
                        pageCount = state.pages.size.coerceAtLeast(1),
                        anchor = state.viewportAnchor,
                        ctrl = event.isCtrlPressed,
                        shift = event.isShiftPressed,
                    ),
                )
                result.action?.let(::handleInput)
                result.consumed
            },
    ) {
        val isWebtoon = state.mode == ReadingMode.WEBTOON
        val currentCrop = if (isWebtoon) settings.cropBordersWebtoon else settings.cropBorders
        ReaderGestureArea(
            enabled = ready,
            onPress = { handlePress(it) },
            onTap = { handleTap(it) },
            onDoubleTap = { handleDoubleTap(it) },
            onPan = { delta, viewport -> handlePan(delta, viewport) },
            onZoomBy = { factor, centroid, viewport -> handleZoomBy(factor, centroid, viewport) },
            modifier = Modifier.fillMaxSize().testTag("reader-gesture-area"),
            onLongPress = { openPageActions() },
            onSecondaryClick = { openPageActions() },
            onPointerMove = {
                overlayVisibility = reduceReaderOverlayVisibility(
                    overlayVisibility,
                    ReaderOverlayEvent.PointerMoved,
                )
                hideGeneration++
            },
        ) {
            CompositionLocalProvider(
                LocalReaderColorFilter provides settings.colorFilter,
                LocalReaderCropBorders provides currentCrop,
                LocalReaderForeground provides state.foreground,
                LocalReaderSelectedPage provides state.pages.getOrNull(state.selectedIndex)?.id,
                LocalReaderPageSizeSink provides pageSizeSink,
                LocalReaderPageImageStore provides imageStore,
            ) {
                ReaderBody(
                    state = state,
                    session = session,
                    onAction = session::dispatch,
                    onRetryChapter = onRetryChapter,
                    backgroundColor = settings.backgroundColor,
                    webtoonMaxWidth = settings.webtoonMaxWidth,
                    webtoonSidePadding = settings.webtoonSidePadding,
                    pageSizes = effectivePageSizes,
                    pageContent = pageContent,
                )
            }
            if (ready) {
                ReaderClickRegions(
                    policy = clickPolicy,
                    onAction = { handleClickAction(it) },
                )
            }
        }
        ReaderChrome(
            state = state,
            title = title,
            chapterTitle = chapterTitle,
            settings = settings,
            visible = overlayVisibility.chromeVisible,
            canRetry = state.loadState is ReaderLoadState.Failed || state.error != null,
            debugEnabled = debugEnabled,
            onBack = {
                closeAndThen(onBack)
            },
            onMode = { applySettings(settings.copy(mode = it)) },
            onScale = { applySettings(settings.copy(scaleMode = it)) },
            onCoverOffset = { applySettings(settings.copy(coverOffset = it)) },
            onZoom = { session.dispatch(ReaderAction.SetZoom(ReaderLayout.clampZoom(it))) },
            onRetry = {
                scope.launch {
                    state.pages.getOrNull(state.selectedIndex)?.id?.let { session.retry(it) } ?: onRetryChapter()
                }
            },
            onFullscreen = onFullscreen,
            onBorderless = onBorderless,
            onOpenSettings = { showSettings = true },
            onColorFilter = { applySettings(settings.copy(colorFilter = it)) },
            onBackgroundColor = { applySettings(settings.copy(backgroundColor = it)) },
            onCropBorders = { applySettings(settings.copy(cropBorders = it)) },
            onCropBordersWebtoon = { applySettings(settings.copy(cropBordersWebtoon = it)) },
            onPageSelected = {
                session.dispatch(ReaderAction.SelectPage(it))
                markReadingInput()
            },
            onPreviousPage = {
                dispatchCore(ReaderAction.Previous)
                markReadingInput()
            },
            onNextPage = {
                dispatchCore(ReaderAction.Next)
                markReadingInput()
            },
            onPreviousChapter = {
                if (settings.alwaysShowChapterTransition) {
                    pendingExternalChapterDirection = ReaderChapterTransitionDirection.PREVIOUS
                    chapterTransition = buildChapterTransition(ReaderChapterTransitionDirection.PREVIOUS)
                } else {
                    navigateExternalChapter(ReaderChapterTransitionDirection.PREVIOUS)
                }
            },
            onNextChapter = {
                if (settings.alwaysShowChapterTransition) {
                    pendingExternalChapterDirection = ReaderChapterTransitionDirection.NEXT
                    chapterTransition = buildChapterTransition(ReaderChapterTransitionDirection.NEXT)
                } else {
                    navigateExternalChapter(ReaderChapterTransitionDirection.NEXT)
                }
            },
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            bookmarked = bookmarked,
            onToggleBookmark = ::toggleChapterBookmark,
            onOpenPageActions = ::openPageActions,
            onOpenShortcuts = { showShortcuts = true },
            chapterCatalog = chapterCatalog,
            currentChapterId = currentChapter?.id,
            onChapterSelected = { targetId ->
                closeAndThen { onChapterSelected?.invoke(targetId) }
            },
        )
        ReaderEdgeReveal(
            tag = "reader-top-reveal",
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            overlayVisibility = reduceReaderOverlayVisibility(overlayVisibility, ReaderOverlayEvent.PointerAtEdge)
            hideGeneration++
        }
        ReaderEdgeReveal(
            tag = "reader-bottom-reveal",
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            overlayVisibility = reduceReaderOverlayVisibility(overlayVisibility, ReaderOverlayEvent.PointerAtEdge)
            hideGeneration++
        }
    }
    chapterTransition?.let { transition ->
        ReaderChapterTransitionSurface(
            transition = transition,
            settings = settings,
            allowDismiss = pendingExternalChapterDirection != null,
            onContinue = {
                val externalDirection = pendingExternalChapterDirection
                chapterTransition = null
                pendingExternalChapterDirection = null
                onChapterTransitionContinue?.invoke(transition.direction)
                if (externalDirection != null) {
                    navigateExternalChapter(externalDirection, transition.target?.id)
                }
            },
            onDismiss = {
                chapterTransition = null
                pendingExternalChapterDirection = null
            },
        )
    }
    pageActionsTarget?.let { target ->
        ReaderPageActionsDialog(
            onDismissRequest = { pageActionsTarget = null },
            canSetAsCover = target.mangaId != null,
            canOpenInBrowser = target.canOpenInBrowser,
            onSave = {
                runPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@runPageAction false
                    handler.saveImage(pageTarget, image)
                }
            },
            onCopy = {
                runPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@runPageAction false
                    handler.copyImage(image)
                }
            },
            onShare = {
                runPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@runPageAction false
                    handler.shareImage(image)
                }
            },
            onSetAsCover = {
                runPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@runPageAction false
                    handler.setAsCover(pageTarget, image)
                }
            },
            onOpenInBrowser = {
                runPageAction(target) { handler, pageTarget ->
                    pageTarget.pageUrl?.let(handler::openInBrowser) ?: false
                }
            },
        )
    }
    pageActionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { pageActionMessage = null },
            title = { Text("Page action") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = { pageActionMessage = null },
                    modifier = Modifier.testTag("reader-page-action-message-ok"),
                ) {
                    Text(strings.dialogOk)
                }
            },
        )
    }
    if (showSettings) {
        ReaderSettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = {
                applySettings(it)
                showSettings = false
            },
        )
    }
    if (showShortcuts) {
        ReaderShortcutsDialog(
            onDismiss = { showShortcuts = false },
        )
    }
}

private fun KeyEvent.toReaderInputKey(): ReaderInputKey? = when (key) {
    Key.DirectionLeft -> ReaderInputKey.LEFT
    Key.DirectionRight -> ReaderInputKey.RIGHT
    Key.A -> ReaderInputKey.A
    Key.D -> ReaderInputKey.D
    Key.Spacebar -> ReaderInputKey.SPACE
    Key.PageUp -> ReaderInputKey.PAGE_UP
    Key.PageDown -> ReaderInputKey.PAGE_DOWN
    Key.MoveHome -> ReaderInputKey.HOME
    Key.MoveEnd -> ReaderInputKey.END
    Key.Plus, Key.Equals -> ReaderInputKey.PLUS
    Key.Minus -> ReaderInputKey.MINUS
    Key.Zero -> ReaderInputKey.ZERO
    Key.F -> ReaderInputKey.F
    Key.F11 -> ReaderInputKey.F11
    Key.B -> ReaderInputKey.B
    Key.Escape -> ReaderInputKey.ESCAPE
    Key.X -> ReaderInputKey.X
    else -> null
}

@Composable
private fun ReaderBody(
    state: ReaderState,
    session: ReaderSession,
    onAction: (ReaderAction) -> Unit,
    onRetryChapter: suspend () -> Unit,
    backgroundColor: ReaderBackgroundColor,
    webtoonMaxWidth: Int,
    webtoonSidePadding: Int,
    pageSizes: Map<PageId, PageSize>,
    pageContent: ReaderPageContent,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    when (val load = state.loadState) {
        ReaderLoadState.Idle,
        is ReaderLoadState.Loading,
        -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(strings.readerLoadingChapter, modifier = Modifier.padding(top = 12.dp).testTag("reader-loading"))
            }
        }
        ReaderLoadState.Ready -> ReaderCanvas(
            state = state,
            onAction = onAction,
            backgroundColor = backgroundColor,
            webtoonMaxWidth = webtoonMaxWidth,
            webtoonSidePadding = webtoonSidePadding,
            pageSizes = pageSizes,
            modifier = Modifier.fillMaxSize(),
            pageContent = pageContent,
        )
        is ReaderLoadState.Failed -> ReaderErrorPanel(
            message = strings.readerErrorMessage(load.error),
            retryable = load.error.code != ReaderErrorCode.EMPTY_CHAPTER,
            onRetry = {
                scope.launch {
                    state.pages.getOrNull(state.selectedIndex)?.id?.let { session.retry(it) } ?: onRetryChapter()
                }
            },
        )
        ReaderLoadState.Closed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.readerClosed, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReaderErrorPanel(message: String, retryable: Boolean, onRetry: () -> Unit) {
    val strings = LocalStrings.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    message,
                    modifier = Modifier.testTag("reader-error"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (retryable) {
                    FilledTonalButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 12.dp).testTag("reader-error-retry"),
                    ) { Text(strings.downloadsRetry) }
                }
            }
        }
    }
}

@Composable
private fun ReaderClickRegions(policy: ClickRegionPolicy, onAction: (ReaderClickAction) -> Unit) {
    val regions = policy.regions
    Row(Modifier.fillMaxSize().testTag("reader-input-surface")) {
        ClickRegion(
            tag = "reader-previous-region",
            weight = (regions[0].end - regions[0].start) * 100f,
            action = regions[0].action,
            onAction = onAction,
        )
        ClickRegion(
            tag = "reader-center-region",
            weight = (regions[1].end - regions[1].start) * 100f,
            action = regions[1].action,
            onAction = onAction,
        )
        ClickRegion(
            tag = "reader-next-region",
            weight = (regions[2].end - regions[2].start) * 100f,
            action = regions[2].action,
            onAction = onAction,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ClickRegion(
    tag: String,
    weight: Float,
    action: ReaderClickAction,
    onAction: (ReaderClickAction) -> Unit,
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .testTag(tag)
            .semantics {
                onClick {
                    onAction(action)
                    true
                }
            },
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun BoxScope.ReaderEdgeReveal(tag: String, modifier: Modifier = Modifier, onReveal: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TOP_REVEAL_HEIGHT)
            .testTag(tag)
            .onPointerEvent(PointerEventType.Move) { onReveal() },
    )
}

@Composable
private fun ReaderPagePlaceholder(pageIndex: Int, modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (pageIndex + 1).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

fun readerErrorMessage(error: ReaderSessionError): String = EnglishStrings.readerErrorMessage(error)

private fun ReaderState.toDesktopSettings() = DesktopReaderSettings(
    mode = mode,
    coverOffset = coverOffset,
    scaleMode = scaleMode,
)

/** Synchronous pan target used while asynchronous session dispatches catch up. */
private class ReaderPanAccumulator {
    var value: ReaderPan? = null
}

private const val CHROME_HIDE_DELAY_MILLIS = 2_500L
private val TOP_REVEAL_HEIGHT = 24.dp
