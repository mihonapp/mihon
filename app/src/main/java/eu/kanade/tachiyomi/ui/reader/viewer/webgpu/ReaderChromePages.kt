package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import ca.mpreg.webgpuviewer.viewer.ComposePageSize
import ca.mpreg.webgpuviewer.viewer.ImagePage
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

/** The side padding `PagerTransitionHolder` gave the same transition view. */
private const val TRANSITION_SIDE_PADDING = 64

/** The padding `WebtoonTransitionHolder` gave it, which took only the height it needed. */
private const val STRIP_TRANSITION_SIDE_PADDING = 32
private const val STRIP_TRANSITION_VERTICAL_PADDING = 128

/** How much of the viewport `WebtoonPageHolder` gave its error layout. */
private const val STRIP_ERROR_HEIGHT_FRACTION = 0.8f

/** The top margin `WebtoonPageHolder` gave its spinner, as a fraction of the viewport. */
private const val STRIP_PROGRESS_TOP_FRACTION = 0.25f

/**
 * The reader's own chapter and page chrome, as composables, in the reader's theme.
 *
 * Built on the reader background, which is fixed for the page's life: a theme change rebuilds every
 * page (see [WebGpuViewer]'s image property listener).
 */
abstract class ReaderComposePage(
    protected val viewer: WebGpuViewer,
    size: ComposePageSize = ComposePageSize.Auto,
) : ImagePage.Compose(size, viewer.readerBackgroundColor()) {

    /**
     * The theme goes on here because the library's page knows nothing of the app's colours.
     *
     * Under the reader's own night mode rather than the app's: the page sits on the reader
     * background, so a light app over a black reader would otherwise draw dark text on black.
     * [TachiyomiTheme] reads that from the configuration, which the pager and webtoon adapters
     * used to get by inflating their views from a reader-themed context.
     */
    @Composable
    final override fun BoxScope.Content() {
        val context = viewer.readerThemedContext
        CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides context.resources.configuration,
        ) {
            TachiyomiTheme { PageContent() }
        }
    }

    @Composable
    protected abstract fun PageContent()
}

/**
 * A page that failed to load or decode, with the pager's error layout: the message, Retry, and
 * Open in WebView for an image that came off the web.
 */
class ErrorPage internal constructor(
    viewer: WebGpuViewer,
    private val viewerPage: WebGpuViewer.ViewerReaderPage?,
    private val error: Throwable?,
) : ReaderComposePage(
    viewer,
    // WebtoonPageHolder gave its error layout less than a screen, so the page below shows through.
    if (viewer is WebGpuViewerContinuous) ComposePageSize.Wrap else ComposePageSize.Auto,
) {

    @Composable
    override fun PageContent() {
        val context = LocalContext.current
        // As the pager's own error layout: only offered for an image that came off the web.
        val imageUrl = viewerPage?.page?.imageUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
        val strip = if (viewer is WebGpuViewerContinuous) {
            Modifier.fillMaxHeight(STRIP_ERROR_HEIGHT_FRACTION).wrapContentHeight()
        } else {
            Modifier
        }

        ReaderError(
            modifier = strip,
            message = with(context) { error?.formattedMessage },
            onRetry = { viewerPage?.let { viewer.retryLoad(it) } },
            onOpenInWebView = imageUrl?.let {
                {
                    val sourceId = viewer.activity.viewModel.manga?.source
                    context.startActivity(WebViewActivity.newIntent(context, it, sourceId))
                }
            },
        )
    }
}

/** `reader_error.xml`, in Compose. */
@Composable
private fun ReaderError(
    message: String?,
    onRetry: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message ?: stringResource(MR.strings.decode_image_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(MR.strings.action_retry))
        }
        if (onOpenInWebView != null) {
            Button(onClick = onOpenInWebView) {
                Text(stringResource(MR.strings.action_open_in_web_view))
            }
        }
    }
}

/** A page still downloading: the reader's progress ring. */
class ProgressPage(viewer: WebGpuViewer) : ReaderComposePage(viewer) {

    /**
     * 0 to 1. On the page, not in the composition, which is dropped when the page goes out of
     * reach. Written from the loader's collector; snapshot state takes writes from any thread.
     */
    var progress: Float
        get() = progressState
        set(value) {
            progressState = value.fastCoerceIn(0f, 1f)
        }

    private var progressState by mutableFloatStateOf(0f)

    @Composable
    override fun PageContent() {
        if (viewer !is WebGpuViewerContinuous) {
            CombinedCircularProgressIndicator(progress = { progress })
            return
        }
        // WebtoonPageHolder centred its spinner below a quarter-height top margin, which puts it
        // at five eighths of the slot rather than halfway down.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = maxHeight * STRIP_PROGRESS_TOP_FRACTION),
                contentAlignment = Alignment.Center,
            ) {
                CombinedCircularProgressIndicator(progress = { progress })
            }
        }
    }
}

/** The page between two chapters, or before the first and after the last. */
class TransitionPage(
    viewer: WebGpuViewer,
    val prevChapter: ReaderChapter?,
    val nextChapter: ReaderChapter?,
) : ReaderComposePage(viewer, ComposePageSize.Wrap) {

    /** As the pager and webtoon adapters framed it: leaving [prevChapter] for [nextChapter]. */
    private val transition: ChapterTransition? = when {
        prevChapter != null -> ChapterTransition.Next(prevChapter, nextChapter)
        nextChapter != null -> ChapterTransition.Prev(nextChapter, null)
        else -> null
    }

    @Composable
    override fun PageContent() {
        val transition = transition ?: return
        val goingToChapterDownloaded by produceState(false, transition) {
            value = withContext(Dispatchers.IO) { isDownloaded(transition) }
        }

        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodySmall,
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
            // The padding the holder each viewer used to inflate this into gave it.
            val padding = if (viewer is WebGpuViewerContinuous) {
                Modifier.padding(
                    horizontal = STRIP_TRANSITION_SIDE_PADDING.dp,
                    vertical = STRIP_TRANSITION_VERTICAL_PADDING.dp,
                )
            } else {
                Modifier.padding(horizontal = TRANSITION_SIDE_PADDING.dp)
            }
            Box(modifier = padding) {
                ChapterTransition(
                    transition = transition,
                    currChapterDownloaded = transition.from.pageLoader?.isLocal == true,
                    goingToChapterDownloaded = goingToChapterDownloaded,
                )
            }
        }
    }

    /** `ReaderTransitionView.bind`'s answer, which reads the disk. */
    private suspend fun isDownloaded(transition: ChapterTransition): Boolean {
        val manga = viewer.activity.viewModel.manga ?: return false
        if (manga.isLocal()) return true
        val chapter = transition.to?.chapter ?: return false
        val source = viewer.activity.viewModel.source ?: return false
        return viewer.downloadManager.isChapterDownloadedOnDisk(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.title,
            source = source,
        )
    }
}
