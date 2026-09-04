package mihon.desktop.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderClickAction
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReaderLayout
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderSessionError
import mihon.reader.session.ReaderState
import mihon.reader.source.ReaderFailure

@Composable
fun ReaderScreen(
    session: ReaderSession,
    title: String,
    chapterTitle: String,
    settingsStore: DesktopReaderSettingsStore?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    onBorderless: () -> Unit = {},
    onRetryChapter: suspend () -> Unit = {},
    foreground: Boolean = true,
    debugEnabled: Boolean = System.getenv("MIHON_W_READER_DEBUG") == "1",
    pageContent: ReaderPageContent = { _, pageIndex, contentModifier ->
        ReaderPagePlaceholder(pageIndex, contentModifier)
    },
) {
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var settings by remember(settingsStore) {
        mutableStateOf(settingsStore?.load() ?: state.toDesktopSettings())
    }
    var chromeVisible by remember { mutableStateOf(true) }
    var hideGeneration by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    fun markReadingInput() {
        chromeVisible = true
        hideGeneration++
    }

    fun applySettings(updated: DesktopReaderSettings) {
        settings = updated
        settingsStore?.save(updated)
        session.dispatch(ReaderAction.ChangeMode(updated.mode))
        session.dispatch(ReaderAction.SetScaleMode(updated.scaleMode))
        session.dispatch(ReaderAction.SetCoverOffset(updated.coverOffset))
    }

    LaunchedEffect(hideGeneration) {
        if (hideGeneration > 0) {
            delay(CHROME_HIDE_DELAY_MILLIS)
            chromeVisible = false
        }
    }
    LaunchedEffect(session, foreground) {
        session.dispatch(ReaderAction.SetForeground(foreground))
    }
    DisposableEffect(session) {
        session.dispatch(ReaderAction.SetContentVisible(true))
        onDispose { session.dispatch(ReaderAction.SetContentVisible(false)) }
    }

    Box(modifier = modifier.fillMaxSize().testTag("reader-screen")) {
        ReaderBody(
            state = state,
            session = session,
            onAction = session::dispatch,
            onRetryChapter = onRetryChapter,
            pageContent = pageContent,
        )
        if (state.loadState is ReaderLoadState.Ready) {
            ReaderClickRegions(
                settings = settings,
                onAction = { action ->
                    when (action) {
                        ReaderClickAction.PREVIOUS -> {
                            session.dispatch(ReaderAction.Previous)
                            markReadingInput()
                        }
                        ReaderClickAction.NEXT -> {
                            session.dispatch(ReaderAction.Next)
                            markReadingInput()
                        }
                        ReaderClickAction.TOGGLE_CHROME -> {
                            chromeVisible = !chromeVisible
                        }
                        ReaderClickAction.NONE -> markReadingInput()
                    }
                },
            )
        }
        ReaderChrome(
            state = state,
            title = title,
            chapterTitle = chapterTitle,
            settings = settings,
            visible = chromeVisible,
            canRetry = state.loadState is ReaderLoadState.Failed || state.error != null,
            debugEnabled = debugEnabled,
            onBack = {
                scope.launch {
                    session.closeAndFlush()
                    onBack()
                }
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
        )
        TopEdgeReveal { chromeVisible = true }
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
}

@Composable
private fun ReaderBody(
    state: ReaderState,
    session: ReaderSession,
    onAction: (ReaderAction) -> Unit,
    onRetryChapter: suspend () -> Unit,
    pageContent: ReaderPageContent,
) {
    val scope = rememberCoroutineScope()
    when (val load = state.loadState) {
        ReaderLoadState.Idle,
        is ReaderLoadState.Loading,
        -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Loading chapter…", modifier = Modifier.padding(top = 12.dp).testTag("reader-loading"))
            }
        }
        ReaderLoadState.Ready -> ReaderCanvas(
            state = state,
            onAction = onAction,
            modifier = Modifier.fillMaxSize(),
            pageContent = pageContent,
        )
        is ReaderLoadState.Failed -> ReaderErrorPanel(
            message = readerErrorMessage(load.error),
            retryable = load.error.code != ReaderErrorCode.EMPTY_CHAPTER,
            onRetry = {
                scope.launch {
                    state.pages.getOrNull(state.selectedIndex)?.id?.let { session.retry(it) } ?: onRetryChapter()
                }
            },
        )
        ReaderLoadState.Closed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Reader closed", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReaderErrorPanel(message: String, retryable: Boolean, onRetry: () -> Unit) {
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
                    ) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun ReaderClickRegions(settings: DesktopReaderSettings, onAction: (ReaderClickAction) -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxSize().testTag("reader-input-surface")) {
        ClickRegion(
            tag = "reader-previous-region",
            weight = settings.clickRegions.leftEndPercent.toFloat(),
            source = source,
        ) { onAction(settings.clickRegions.leftAction) }
        ClickRegion(
            tag = "reader-center-region",
            weight = (settings.clickRegions.centerEndPercent - settings.clickRegions.leftEndPercent).toFloat(),
            source = source,
        ) { onAction(settings.clickRegions.centerAction) }
        ClickRegion(
            tag = "reader-next-region",
            weight = (100 - settings.clickRegions.centerEndPercent).toFloat(),
            source = source,
        ) { onAction(settings.clickRegions.rightAction) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ClickRegion(
    tag: String,
    weight: Float,
    source: MutableInteractionSource,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .testTag(tag)
            .clickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TopEdgeReveal(onReveal: () -> Unit) {
    val thresholdPixels = with(LocalDensity.current) { TOP_REVEAL_HEIGHT.roundToPx().toFloat() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(TOP_REVEAL_HEIGHT)
            .testTag("reader-top-reveal")
            .onPointerEvent(PointerEventType.Move) { event ->
                if (event.changes.any { it.position.y <= thresholdPixels }) onReveal()
            },
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

fun readerErrorMessage(error: ReaderSessionError): String = when (error.cause) {
    is ReaderFailure.PageNotFound -> "This page could not be found. It may have been moved or deleted."
    is ReaderFailure.UnsupportedFormat -> "This chapter format is not supported."
    is ReaderFailure.EncryptedContainer -> "Encrypted chapter containers are not supported."
    is ReaderFailure.UnsafePath,
    is ReaderFailure.ResourceChanged,
    -> "This local chapter is no longer available. Locate or re-import it."
    is ReaderFailure.CorruptContainer,
    is ReaderFailure.CorruptImage,
    -> "This page is corrupt or unreadable."
    is ReaderFailure.UnsupportedImage,
    is ReaderFailure.RegionUnavailable,
    -> "This image format is not supported."
    is ReaderFailure.LimitExceeded,
    is ReaderFailure.TooManyEntries,
    -> "This chapter exceeds the safe reader limits."
    is ReaderFailure.EmptyChapter -> "This chapter contains no readable pages."
    else -> when (error.code) {
        ReaderErrorCode.EMPTY_CHAPTER -> "This chapter contains no readable pages."
        ReaderErrorCode.SOURCE_UNAVAILABLE -> "This local chapter is unavailable. Locate or re-import it."
        ReaderErrorCode.PAGE_NOT_FOUND -> "This page could not be found."
        ReaderErrorCode.PAGE_DECODE_FAILED -> "This page could not be decoded."
        ReaderErrorCode.MEMORY_LIMIT_REACHED -> "The reader reached its memory limit."
        ReaderErrorCode.INVALID_PROGRESS -> "The saved reading position is invalid."
    }
}

private fun ReaderState.toDesktopSettings() = DesktopReaderSettings(
    mode = mode,
    coverOffset = coverOffset,
    scaleMode = scaleMode,
)

private const val CHROME_HIDE_DELAY_MILLIS = 2_500L
private val TOP_REVEAL_HEIGHT = 24.dp
