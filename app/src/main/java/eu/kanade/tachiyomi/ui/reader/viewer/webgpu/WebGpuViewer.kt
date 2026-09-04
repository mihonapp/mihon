package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.Color
import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.webgpu.GPUTexture
import ca.mpreg.imagedecoder.ImageDecoder
import ca.mpreg.webgpuviewer.ImageView
import ca.mpreg.webgpuviewer.closeTo
import ca.mpreg.webgpuviewer.draw.TextAlign
import ca.mpreg.webgpuviewer.renderer.Image
import ca.mpreg.webgpuviewer.transition.TransitionBasic
import ca.mpreg.webgpuviewer.transition.TransitionCube
import ca.mpreg.webgpuviewer.transition.TransitionCubeOuter
import ca.mpreg.webgpuviewer.transition.TransitionFade
import ca.mpreg.webgpuviewer.transition.TransitionFadeWhite
import ca.mpreg.webgpuviewer.transition.TransitionFlip
import ca.mpreg.webgpuviewer.transition.TransitionFlipLeft
import ca.mpreg.webgpuviewer.transition.TransitionFlipRight
import ca.mpreg.webgpuviewer.transition.TransitionNone
import ca.mpreg.webgpuviewer.transition.TransitionSphere
import ca.mpreg.webgpuviewer.transition.TransitionStackDown
import ca.mpreg.webgpuviewer.transition.TransitionStackLeft
import ca.mpreg.webgpuviewer.transition.TransitionStackRight
import ca.mpreg.webgpuviewer.transition.TransitionStackUp
import ca.mpreg.webgpuviewer.viewer.ImagePage
import ca.mpreg.webgpuviewer.viewer.ImageViewerContinuousState
import com.google.android.material.color.MaterialColors
import de.stefan_oltmann.kim.Kim
import de.stefan_oltmann.kim.android.readMetadata
import de.stefan_oltmann.kim.format.tiff.constant.TiffTag
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import java.util.TreeSet
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

open class WebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
    val pager: ImageView = ImageView(activity, isVertical = isVertical, isReversed = isReversed),
) : Viewer {

    open val isContinuous: Boolean = false

    val readerPreferences by lazy { activity.appGraph.readerPreferences }

    private fun readerBackgroundColor(): Int = activity.baseContext.readerBackgroundColor(config.theme)

    private fun readerOnBackgroundColor(): Int = MaterialColors.getColor(
        activity.createReaderThemeContext(),
        com.google.android.material.R.attr.colorOnBackground,
        Color.WHITE,
    )

    private val scope = MainScope()

    // Dedicated thread for decode worker to avoid blocking Dispatchers.Default pool
    private val decodeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "WebGpuViewer-Decode").apply { isDaemon = true }
    }
    private val decodeDispatcher = decodeExecutor.asCoroutineDispatcher()

    // Single lock for all page cache and queue operations
    private val lock = Object()

    // Page cache - keyed by stable PageKey for O(1) lookup
    private val pageCache = LinkedHashMap<PageKey, ViewerPage>()

    // Decode queue - pages waiting to be decoded, processed LIFO (last = highest priority)
    private val decodeQueue = ArrayDeque<ViewerReaderPage>()

    /**
     * Indices of the pages that take a spread to themselves, by chapter - see [spreadStartIndex].
     * Outlives [pageCache]: every page after one of these depends on it, long since evicted.
     */
    private val loneIndices = HashMap<Long?, TreeSet<Int>>()

    /**
     * Which side of a dual-page spread a [ViewerReaderPage] belongs on - app-level bookkeeping
     * for [getSpreadAnchor]/[buildSpreadPage], independent of the decoded image itself.
     */
    internal enum class SpreadPosition { LEFT, RIGHT, SINGLE }

    /** Above this, an untagged page is a spread already, not half of one. */
    private val wideAspect = 1.2f

    /** How far two untagged pages' aspect ratios may differ and still pair. */
    private val pairAspectTolerance = 0.1f

    // Stable key types for page identity - data classes provide correct equals/hashCode
    private sealed class PageKey {
        data class Reader(val chapterId: Long?, val index: Int) : PageKey()
        data class Transition(val prevId: Long?, val nextId: Long?) : PageKey()
    }

    private fun pageKey(page: ViewerPage): PageKey = when (page) {
        is ViewerReaderPage -> PageKey.Reader(page.page.chapter.chapter.id, page.page.index)
        is ViewerTransitionPage -> PageKey.Transition(page.prevChapter?.chapter?.id, page.nextChapter?.chapter?.id)
        else -> PageKey.Transition(null, null)
    }

    private fun findInCache(key: PageKey): ViewerPage? = pageCache[key]

    /** Check if a page is in the cache by identity. O(1) via key lookup. */
    private fun pageInCache(page: ViewerPage): Boolean = pageCache[pageKey(page)] === page

    /**
     * Queue a page for decoding if not already queued/loading/decoded.
     * If prioritize=true and page is already queued, moves it to front.
     * Must be called while holding lock.
     */
    private fun queueForDecode(page: ViewerReaderPage, prioritize: Boolean = false) {
        // Already has a decoded image
        if (page.isDecoded) return

        when (page.state) {
            PageState.IDLE -> {
                page.state = PageState.QUEUED
                if (prioritize) {
                    decodeQueue.addLast(page)
                } else {
                    decodeQueue.addFirst(page)
                }
                lock.notify()
            }

            PageState.QUEUED -> {
                // Already queued - move to front if prioritizing
                if (prioritize && decodeQueue.remove(page)) {
                    decodeQueue.addLast(page)
                }
            }

            PageState.LOADING, PageState.DECODING -> {
                // Already being processed
            }
        }
    }

    init {
        // Decode worker thread - processes pages from the queue
        scope.launch(decodeDispatcher) {
            try {
                while (true) {
                    val page = synchronized(lock) {
                        while (decodeQueue.isEmpty()) {
                            lock.wait()
                        }
                        decodeQueue.removeLast().apply { state = PageState.DECODING }
                    }

                    // Verify page is still valid (not evicted and doesn't have a decoded image yet)
                    val shouldProcess = synchronized(lock) {
                        pageInCache(page) && page.state == PageState.DECODING && !page.isDecoded
                    }

                    if (!shouldProcess) {
                        synchronized(lock) {
                            if (pageInCache(page)) page.state = PageState.IDLE
                        }
                        continue
                    }

                    try {
                        decodeReaderPage(page)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "decodeReaderPage: ${e.message}" }
                        synchronized(lock) {
                            if (pageInCache(page) && !page.isDecoded && !page.imagePage.destroyed) {
                                val oldImagePage = page.imagePage
                                val errorMessage = e.message ?: "Failed to decode image"
                                page.imagePage = ErrorPage(errorMessage, page.spreadPosition)
                                page.state = PageState.IDLE
                                oldImagePage.cleanup()
                                page.imagePage.invalidate()
                            } else {
                                if (pageInCache(page)) page.state = PageState.IDLE
                            }
                        }
                    }
                }
            } catch (_: InterruptedException) {
                // Normal shutdown
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Decode worker died" }
            }
        }
    }

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = WebGpuConfig(this, scope, readerPreferences)

    var viewerChapters: ViewerChapters? = null

    val pages: List<ReaderPage>? get() = (currentPage as? ViewerReaderPage)?.page?.chapter?.pages

    @Volatile
    var currentPage: ViewerPage? = null

    /**
     * What a running page turn animates away from, kept out of [evictFarthestPage]'s reach - a
     * jump preloads enough pages to evict it. Replaced by the next turn's rather than cleared.
     */
    @Volatile
    private var pinnedFromPage: ImagePage? = null

    /** True while [pinnedFromPage] is drawing [page]'s image, as itself or as a spread side. */
    private fun isPinned(page: ViewerPage): Boolean {
        val pinned = pinnedFromPage ?: return false
        val image = page.imagePage
        if (pinned === image) return true
        return pinned is ImagePage.ImageSpread && (pinned.left === image || pinned.right === image)
    }

    open val preloadAhead = 3
    open val preloadBehind = 2

    /**
     * Everything [preloadPages] reaches, plus slack. Sized exactly, a chapter transition page - or
     * in dual mode a spread partner - evicts a page the next fetch asks for, and it decodes again.
     */
    open val cacheSize get() = 1 + preloadAhead + preloadBehind + if (isDualPageMode()) 3 else 1

    /**
     * Page processing state
     */
    enum class PageState {
        IDLE,
        QUEUED,
        LOADING,
        DECODING,
    }

    /**
     * Evicts the page farthest from reference. Must be called while holding lock.
     *
     * Never evicts [reference], [currentPage] or what [pinnedFromPage] draws. Returns false when
     * nothing was evictable, so a trim loop stops instead of spinning.
     *
     * @param reference The page to use as reference (defaults to currentPage)
     */
    private fun evictFarthestPage(reference: ViewerPage? = null): Boolean {
        val current = reference ?: currentPage ?: return false
        val candidates =
            pageCache.values.filter { it !== current && it !== currentPage && !isPinned(it) }.toMutableSet()
        if (candidates.isEmpty()) return false

        fun findNext(page: ViewerPage): ViewerPage? = when (page) {
            is ViewerReaderPage -> {
                val chapterId = page.page.chapter.chapter.id
                val nextIndex = page.page.index + 1
                candidates.find {
                    it is ViewerReaderPage && it.page.chapter.chapter.id == chapterId && it.page.index == nextIndex
                } ?: candidates.find { it is ViewerTransitionPage && it.prevChapter?.chapter?.id == chapterId }
                    ?: page.nextChapter?.chapter?.id?.let { nextChapterId ->
                        candidates.find {
                            it is ViewerReaderPage && it.page.chapter.chapter.id == nextChapterId && it.page.index == 0
                        }
                    }
            }

            is ViewerTransitionPage -> {
                val nextChapterId = page.nextChapter?.chapter?.id
                candidates.find {
                    it is ViewerReaderPage && it.page.chapter.chapter.id == nextChapterId && it.page.index == 0
                }
            }

            else -> null
        }

        fun findPrev(page: ViewerPage): ViewerPage? = when (page) {
            is ViewerReaderPage -> {
                val chapterId = page.page.chapter.chapter.id
                val prevIndex = page.page.index - 1
                candidates.find {
                    it is ViewerReaderPage && it.page.chapter.chapter.id == chapterId && it.page.index == prevIndex
                } ?: candidates.find { it is ViewerTransitionPage && it.nextChapter?.chapter?.id == chapterId }
                    ?: page.prevChapter?.let { prevChapter ->
                        prevChapter.pages?.lastIndex?.let { lastIndex ->
                            candidates.find {
                                it is ViewerReaderPage && it.page.chapter.chapter.id == prevChapter.chapter.id &&
                                    it.page.index == lastIndex
                            }
                        }
                    }
            }

            is ViewerTransitionPage -> {
                val prevChapterId = page.prevChapter?.chapter?.id
                page.prevChapter?.pages?.lastIndex?.let { lastIndex ->
                    candidates.find {
                        it is ViewerReaderPage && it.page.chapter.chapter.id == prevChapterId &&
                            it.page.index == lastIndex
                    }
                }
            }

            else -> null
        }

        var farthest: ViewerPage? = null
        var forward: ViewerPage? = current
        var backward: ViewerPage? = current

        for (i in 0 until cacheSize) {
            if (candidates.isEmpty()) break
            forward = forward?.let { findNext(it) }
            backward = backward?.let { findPrev(it) }
            if (forward == null && backward == null) break
            if (forward != null && candidates.remove(forward)) farthest = forward
            if (backward != null && candidates.remove(backward)) farthest = backward
        }

        val toRemove = candidates.firstOrNull() ?: farthest ?: return false

        pageCache.remove(pageKey(toRemove))
        decodeQueue.remove(toRemove)
        toRemove.state = PageState.IDLE
        (toRemove as? ViewerReaderPage)?.spreadPage?.cleanup()
        toRemove.imagePage.cleanup()
        return true
    }

    /**
     * Gets or creates a page. Thread-safe.
     * @param referencePage The page to use as reference for eviction (defaults to currentPage)
     */
    fun getPage(page: ReaderPage, referencePage: ViewerPage? = null): ViewerPage {
        val key = PageKey.Reader(page.chapter.chapter.id, page.index)
        return synchronized(lock) {
            findInCache(key) ?: ViewerReaderPage(page).also { newPage ->
                pageCache[key] = newPage
                while (pageCache.size > cacheSize) {
                    if (!evictFarthestPage(referencePage ?: newPage)) break
                }
            }
        }
    }

    fun getPage(
        prevChapter: ReaderChapter?,
        nextChapter: ReaderChapter?,
        referencePage: ViewerPage? = null,
    ): ViewerPage {
        val key = PageKey.Transition(prevChapter?.chapter?.id, nextChapter?.chapter?.id)
        return synchronized(lock) {
            findInCache(key) ?: ViewerTransitionPage(prevChapter, nextChapter).also { newPage ->
                pageCache[key] = newPage
                while (pageCache.size > cacheSize) {
                    if (!evictFarthestPage(referencePage ?: newPage)) break
                }
            }
        }
    }

    /**
     * Kicks off loading [chapter] and, once its pages actually show up, re-runs
     * [preloadPages] from the current page - [ReaderActivity]'s viewModel.preload isn't
     * guaranteed to have finished loading by the time it returns, so a single immediate
     * retry can race it and silently never queue the adjacent chapter's edge page for
     * decode. Gives up after 5 seconds if the chapter never finishes loading.
     */
    private fun preloadChapterThenRetry(chapter: ReaderChapter) {
        scope.launch(Dispatchers.Default) {
            activity.viewModel.preload(chapter)
            repeat(25) {
                if (chapter.state is ReaderChapter.State.Loaded) {
                    currentPage?.let { preloadPages(it) }
                    return@launch
                }
                delay(200.milliseconds)
            }
        }
    }

    inner class ErrorPage internal constructor(
        message: String,
        private val spreadPosition: SpreadPosition = SpreadPosition.SINGLE,
    ) : ImagePage.Render(0, 0) {
        override val width: Int
            get() = viewportPageWidth(spreadPosition != SpreadPosition.SINGLE)
        override val height: Int
            get() = pager.state.height

        init {
            minScale = 1f
            maxScale = 1f
            homeScale = 1f
        }

        var message: String = message
            set(value) {
                field = value
                invalidate()
            }

        override val backgroundColor: Int = readerBackgroundColor()

        override fun render(dst: GPUTexture, x: Float, y: Float, scale: Float) {
            val padding = with(pager.state.density) { 24.dp.toPx() }
            val size = scale * with(pager.state.density) { 16.dp.toPx() }

            val cx = dst.width * (0.5f + scale * x)
            val cy = dst.height * (0.5f + scale * y)

            text(
                dst,
                activity.baseContext,
                FontFamily.Default,
                message,
                cx,
                cy,
                size,
                color = readerOnBackgroundColor(),
                align = TextAlign.Center,
                maxWidth = dst.width - 2f * padding,
            )
        }
    }

    inner class ProgressPage(foregroundColor: Int = readerOnBackgroundColor()) : ImagePage.Render(0, 0) {
        override val width: Int
            get() = viewportPageWidth(isDualPageMode())
        override val height: Int
            get() = pager.state.height

        init {
            minScale = 1f
            maxScale = 1f
            homeScale = 1f
        }

        var progress: Float = 0f
            set(value) {
                field = value
                invalidate()
            }

        var foregroundColor: Int = foregroundColor
            set(value) {
                field = value
                invalidate()
            }

        override val backgroundColor: Int = readerBackgroundColor()

        override fun render(dst: GPUTexture, x: Float, y: Float, scale: Float) {
            // Its own footprint, so the page carries its background wherever a transition puts it.
            fillPage(dst, x, y, scale, backgroundColor)

            val cx = dst.width * (0.5f + scale * x)
            val cy = dst.height * (0.5f + scale * y)

            // Off this page's own width, not dst's: a spread half would otherwise draw a ring
            // sized for the whole screen, straight over its partner.
            val full = width * 0.5f * scale

            circle(cx, cy, full / 2f, 0xAAAAAAAA.toInt())

            val diameter = full * progress.fastCoerceIn(0f, 1f)
            if (diameter > 0) {
                circle(cx, cy, diameter / 2f, foregroundColor)
            }
        }
    }

    inner class TransitionPage(val prevChapter: ReaderChapter?, val nextChapter: ReaderChapter?) :
        ImagePage.Render(0, 0) {
        /** Square, and never a spread side - [buildSpreadPage] hands it back whole. */
        override val width: Int
            get() = min(pager.state.width, pager.state.height)
        override val height: Int
            get() = width

        init {
            minScale = 1f
            maxScale = 1f
            homeScale = 1f
        }

        override val backgroundColor: Int = readerBackgroundColor()

        override fun render(dst: GPUTexture, x: Float, y: Float, scale: Float) {
            // Its own footprint, so the page carries its background wherever a transition puts it.
            fillPage(dst, x, y, scale, backgroundColor)

            val lines: MutableList<String> = mutableListOf()
            prevChapter?.chapter?.let { chapter -> lines.add("Previous: " + chapter.name) }
            nextChapter?.chapter?.let { chapter -> lines.add("Next: " + chapter.name) }

            val text = lines.joinToString("\n")

            val padding = with(pager.state.density) { 24.dp.toPx() }
            val size = scale * with(pager.state.density) { 16.dp.toPx() }

            val cx = dst.width * (0.5f + scale * x)
            val cy = dst.height * (0.5f + scale * y)

            text(
                dst,
                activity.baseContext,
                FontFamily.Default,
                text,
                cx,
                cy,
                size,
                readerOnBackgroundColor(),
                align = TextAlign.Center,
                maxWidth = dst.width - 2f * padding,
            )
        }
    }

    abstract class ViewerPage {
        abstract val prevChapter: ReaderChapter?
        abstract val nextChapter: ReaderChapter?
        abstract val prev: ViewerPage?
        abstract val next: ViewerPage?

        @Volatile
        var state: PageState = PageState.IDLE

        @Volatile
        open var imagePage: ImagePage = ImagePage.Dummy(400, 400)

        open val isDecoded = true
    }

    inner class ViewerTransitionPage(
        override val prevChapter: ReaderChapter?,
        override val nextChapter: ReaderChapter?,
    ) : ViewerPage() {
        override var imagePage: ImagePage = TransitionPage(prevChapter, nextChapter)

        override val prev: ViewerPage?
            get() = prevChapter?.pages?.lastOrNull()?.let { getPage(it, currentPage) }

        override val next: ViewerPage?
            get() = nextChapter?.pages?.firstOrNull()?.let { getPage(it, currentPage) }
    }

    inner class ViewerReaderPage(val page: ReaderPage) : ViewerPage() {
        /** Cached spread ImagePage when this page is the anchor of a dual-page spread */
        var spreadPage: ImagePage.ImageSpread? = null

        /** The side the file names, or null for none. Never a value merely derived from the index. */
        @Volatile
        internal var taggedSpreadPosition: SpreadPosition? = null

        /** The decoded image's shape, or null while this page is still a placeholder. */
        internal val aspectRatio: Float?
            get() = (imagePage as? ImagePage.ImageSingle)?.let {
                val height = it.trimHeight
                if (it.isDecoded && height > 0) it.trimWidth.toFloat() / height else null
            }

        /**
         * Which half of a spread this page is on - derived until the file tags it. Without that a
         * still-loading page stays SINGLE, never pairs, and its ring draws mid-screen; deriving it
         * live also re-decides it on a rotation in or out of dual mode.
         *
         * Untagged goes by [wideAspect] first, then [derivedSpreadPosition].
         */
        internal val spreadPosition: SpreadPosition
            get() {
                taggedSpreadPosition?.let { return it }
                if (standsAlone) return SpreadPosition.SINGLE
                return derivedSpreadPosition(page)
            }

        /** True when nothing may share this page's spread - it is one already. */
        internal val standsAlone: Boolean
            get() = taggedSpreadPosition == SpreadPosition.SINGLE ||
                (aspectRatio ?: 0f) > wideAspect

        override var imagePage: ImagePage = ProgressPage()

        override val isDecoded
            get() = (imagePage as? ImagePage.ImageSingle)?.isDecoded == true

        override val prevChapter: ReaderChapter?
            get() = when (page.chapter) {
                viewerChapters?.currChapter -> viewerChapters?.prevChapter
                viewerChapters?.nextChapter -> viewerChapters?.currChapter
                else -> null
            }

        override val nextChapter: ReaderChapter?
            get() = when (page.chapter) {
                viewerChapters?.currChapter -> viewerChapters?.nextChapter
                viewerChapters?.prevChapter -> viewerChapters?.currChapter
                else -> null
            }

        override val prev: ViewerPage?
            get() = page.chapter.pages?.let { pages ->
                pages.getOrNull(page.index - 1)?.let { getPage(it, currentPage) } ?: run {
                    val prevChapter = prevChapter ?: return@run getPage(null, page.chapter, currentPage)

                    if (prevChapter.state !is ReaderChapter.State.Loaded) {
                        preloadChapterThenRetry(prevChapter)
                    }

                    if (config.alwaysShowChapterTransition) {
                        getPage(prevChapter, page.chapter, currentPage)
                    } else {
                        prevChapter.pages?.lastOrNull()?.let { getPage(it, currentPage) }
                    }
                }
            }

        override val next: ViewerPage?
            get() = page.chapter.pages?.let { pages ->
                pages.getOrNull(page.index + 1)?.let { getPage(it, currentPage) } ?: run {
                    val nextChapter = nextChapter ?: return@run getPage(page.chapter, null, currentPage)

                    if (nextChapter.state !is ReaderChapter.State.Loaded) {
                        preloadChapterThenRetry(nextChapter)
                    }

                    if (config.alwaysShowChapterTransition) {
                        getPage(page.chapter, nextChapter, currentPage)
                    } else {
                        nextChapter.pages?.firstOrNull()?.let { getPage(it, currentPage) }
                    }
                }
            }
    }

    /** Read live: these pages are built before the surface has a size, and outlive a rotation. */
    private fun viewportPageWidth(half: Boolean): Int = if (half) pager.state.width / 2 else pager.state.width

    /**
     * Check if dual page mode is currently active based on config and view dimensions.
     * Dual page is never active for continuous (scrolling) viewers.
     */
    fun isDualPageMode(): Boolean {
        if (isContinuous) return false
        return when (config.dualPageView) {
            ReaderPreferences.DualPageView.NEVER -> false
            ReaderPreferences.DualPageView.ALWAYS -> true
            ReaderPreferences.DualPageView.WIDE -> {
                val width = pager.state.width
                val height = pager.state.height
                width > 0 && height > 0 && width.toFloat() / height > 1f
            }
        }
    }

    /** The half a spread opens on: right reading right-to-left, left otherwise. */
    private val anchorPosition get() = if (isReversed) SpreadPosition.RIGHT else SpreadPosition.LEFT

    private val partnerPosition get() = if (isReversed) SpreadPosition.LEFT else SpreadPosition.RIGHT

    /**
     * Which half a page falls on when nothing tags the file: alternating from its spread's start,
     * anchor then partner. SINGLE outside dual page mode, so nothing pairs while one page fills
     * the viewer.
     */
    private fun derivedSpreadPosition(page: ReaderPage): SpreadPosition {
        if (!isDualPageMode()) return SpreadPosition.SINGLE
        val offset = page.index - spreadStartIndex(page.chapter.chapter.id, page.index)
        return if (offset >= 0 && offset % 2 == 0) anchorPosition else partnerPosition
    }

    /**
     * Where the spread holding [index] starts: just past the last page before it that took one to
     * itself, so the page after a detected spread opens the next one instead of inheriting a parity
     * that page broke. Defaults to 1 - page 0 is the cover, and pairs with nothing.
     */
    private fun spreadStartIndex(chapterId: Long?, index: Int): Int {
        val lone = synchronized(lock) { loneIndices[chapterId]?.lower(index) } ?: return 1
        return lone + 1
    }

    /** Registers whether [page] stands alone, for [spreadStartIndex]. Must hold [lock]. */
    private fun noteIfLone(page: ViewerReaderPage) {
        val indices = loneIndices.getOrPut(page.page.chapter.chapter.id) { TreeSet() }
        if (page.standsAlone) indices.add(page.page.index) else indices.remove(page.page.index)
    }

    /**
     * Whether these two may share a spread, beyond their positions agreeing. Both tagged is taken
     * as read; a pair resting on page order needs the same shape - halves of one sheet scan alike.
     * Undecoded pairs anyway, or a loading page draws its ring mid-screen.
     */
    private fun canPairShapes(anchor: ViewerReaderPage, partner: ViewerReaderPage): Boolean {
        if (anchor.taggedSpreadPosition != null && partner.taggedSpreadPosition != null) return true
        val a = anchor.aspectRatio ?: return true
        val b = partner.aspectRatio ?: return true
        return abs(a - b) <= pairAspectTolerance
    }

    /**
     * Check if the given page can form a spread with the next page.
     * Uses page.spreadPosition to determine: anchor + partner = spread
     * RTL: RIGHT is anchor, looks for LEFT on next
     * LTR: LEFT is anchor, looks for RIGHT on next
     */
    private fun canFormSpread(page: ViewerReaderPage): Boolean {
        if (!isDualPageMode()) return false
        if (page.spreadPosition != anchorPosition) return false
        val next = page.next as? ViewerReaderPage ?: return false
        if (next.page.chapter != page.page.chapter) return false
        return next.spreadPosition == partnerPosition && canPairShapes(page, next)
    }

    /**
     * Get the anchor page for a spread.
     * RTL: anchor is RIGHT, for LEFT page returns previous RIGHT
     * LTR: anchor is LEFT, for RIGHT page returns previous LEFT
     */
    private fun getSpreadAnchor(page: ViewerPage): ViewerPage {
        if (!isDualPageMode()) return page
        if (page !is ViewerReaderPage) return page

        // If this is a partner page, check if previous is anchor
        if (page.spreadPosition == partnerPosition) {
            val prev = page.prev as? ViewerReaderPage ?: return page
            if (prev.page.chapter == page.page.chapter && prev.spreadPosition == anchorPosition &&
                canPairShapes(prev, page)
            ) {
                return prev
            }
        }

        // This page is the anchor or standalone
        return page
    }

    private fun buildSpreadPage(page: ViewerPage): ImagePage {
        // For ViewerTransitionPage, return its imagePage directly
        if (page !is ViewerReaderPage) {
            return page.imagePage
        }

        // Only form spreads in dual page mode
        if (!isDualPageMode()) {
            return page.imagePage
        }

        // Whatever the page is holding takes its half of the seam, decoded or not:
        // [ImagePage.ImageSpread] draws a [ImagePage.Render] side into its own half. A page left
        // out would take the whole viewport instead, hiding its partner with it.
        val imagePage = page.imagePage

        if (page.spreadPosition == SpreadPosition.SINGLE) {
            page.spreadPage = null
            return imagePage
        }

        // Only the anchor side looks for a partner on the next page. A partner-tagged page only
        // reaches this function directly (rather than being redirected here via
        // [getSpreadAnchor]) when it has no anchor before it - a lone RIGHT with no preceding
        // LEFT (or vice versa), e.g. at a chapter boundary - so it renders alone on its own side
        // instead of looking anywhere else for a partner.
        val partnerImagePage = if (page.spreadPosition == anchorPosition) {
            val nextReaderPage = (page.next as? ViewerReaderPage)?.takeIf { it.page.chapter == page.page.chapter }
            nextReaderPage?.imagePage?.takeIf {
                nextReaderPage.spreadPosition == partnerPosition &&
                    canPairShapes(page, nextReaderPage)
            }
        } else {
            null
        }

        // LEFT/RIGHT map directly to the spread's left/right slot - independent of reading
        // direction, which only decides which side is the anchor for pairing purposes above.
        val left = if (page.spreadPosition == SpreadPosition.LEFT) imagePage else partnerImagePage
        val right = if (page.spreadPosition == SpreadPosition.RIGHT) imagePage else partnerImagePage

        // Reuse existing spread if the sides match - preserves transform state
        val existing = page.spreadPage
        if (existing != null && existing.left === left && existing.right === right) {
            return existing
        }

        // Create new spread. Composes the existing page(s) directly, so either side (or both)
        // keeps animating independently via its own already-running frame loop - no copying of
        // animation state needed. The other slot is simply null when there's no partner (yet).
        val spread = ImagePage.ImageSpread(left, right)
        page.spreadPage = spread
        return spread
    }

    init {
        pager.state.apply {
            fetchPage = fetch@{ index ->
                val current = currentPage ?: return@fetch null

                // For index 0, return the current spread
                if (index == 0) {
                    return@fetch buildSpreadPage(getSpreadAnchor(current))
                }

                // Navigate by spreads from current
                var page = current
                val step = if (index > 0) 1 else -1
                repeat(abs(index)) {
                    page = nextPage(page, step) ?: return@fetch null
                }

                return@fetch buildSpreadPage(page)
            }

            onTap = { offset ->
                when (config.navigator.getAction(PointF(offset.x, offset.y))) {
                    NavigationRegion.MENU -> activity.toggleMenu()
                    NavigationRegion.NEXT -> if (isReversed) moveToPrevious() else moveToNext()
                    NavigationRegion.PREV -> if (isReversed) moveToNext() else moveToPrevious()
                    NavigationRegion.RIGHT -> moveRight()
                    NavigationRegion.LEFT -> moveLeft()
                }
            }

            onLongTap = { _ ->
                if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                    (currentPage as? ViewerReaderPage)?.let { activity.onPageLongTap(it.page) }
                }
            }
        }

        config.imagePropertyChangedListener = {
            val isDual = isDualPageMode()
            pager.state.apply {
                transition = when (if (isDual) config.transitionAnimationDual else config.transitionAnimation) {
                    TransitionAnimation.BASIC -> if (isVertical) TransitionBasic.Vertical else TransitionBasic
                    TransitionAnimation.FLIP -> TransitionFlip
                    TransitionAnimation.FLIP_LEFT -> TransitionFlipLeft
                    TransitionAnimation.FLIP_RIGHT -> TransitionFlipRight
                    TransitionAnimation.STACK_LEFT -> TransitionStackLeft
                    TransitionAnimation.STACK_RIGHT -> TransitionStackRight
                    TransitionAnimation.STACK_UP -> TransitionStackUp
                    TransitionAnimation.STACK_DOWN -> TransitionStackDown
                    TransitionAnimation.SPHERE -> TransitionSphere
                    TransitionAnimation.CUBE_INSIDE -> TransitionCube
                    TransitionAnimation.CUBE_OUTSIDE -> TransitionCubeOuter
                    TransitionAnimation.FADE -> TransitionFade
                    TransitionAnimation.FADE_WHITE -> TransitionFadeWhite
                    TransitionAnimation.NONE -> TransitionNone
                }

                when (if (isDual) config.cutoutModeDual else config.cutoutMode) {
                    ReaderPreferences.CutoutMode.IGNORE -> avoidCutout = false
                    ReaderPreferences.CutoutMode.AVOID -> {
                        avoidCutout = true
                        alwaysAvoidCutout = false
                    }

                    ReaderPreferences.CutoutMode.SHIFT -> {
                        avoidCutout = true
                        alwaysAvoidCutout = true
                    }
                }

                (this as? ImageViewerContinuousState)?.let {
                    minZoomWidthFraction = config.continuousMinWidth / 100f
                    scale = minScale
                }
            }

            synchronized(lock) {
                decodeQueue.clear()
                pageCache.values.forEach {
                    it.state = PageState.IDLE
                    (it as? ViewerReaderPage)?.spreadPage?.cleanup()
                    it.imagePage.cleanup()
                }
                pageCache.clear()

                currentPage = (currentPage as? ViewerReaderPage)?.page?.let { getPage(it) }
                    ?: (currentPage as? ViewerTransitionPage)?.let {
                        getPage(it.prevChapter, it.nextChapter)
                    }

                currentPage?.let { preloadPages(it) }
            }

            pager.state.invalidate()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
    }

    override fun destroy() {
        // Cancel scope first to stop any new operations
        scope.cancel()

        // Shutdown decode executor with interrupt to wake up the worker from wait()
        decodeExecutor.shutdownNow()
        decodeDispatcher.close()

        // Now clean up pages (cleanup() launches fire-and-forget coroutines on Dispatchers.Default)
        synchronized(lock) {
            decodeQueue.clear()
            pageCache.values.forEach {
                it.state = PageState.IDLE
                (it as? ViewerReaderPage)?.spreadPage?.cleanup()
                it.imagePage.cleanup()
            }
            pageCache.clear()
            loneIndices.clear()
            // Notify in case worker is waiting (though it should be interrupted)
            lock.notifyAll()
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View = pager

    /**
     * Start loading a page and set up listener to re-queue when ready.
     * Called when decode worker encounters a page that isn't downloaded yet.
     */
    private fun startPageLoad(page: ViewerReaderPage) {
        val loader = page.page.chapter.pageLoader ?: run {
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            return
        }

        // If page is already ready, just re-queue immediately
        if (page.page.status == Page.State.Ready) {
            synchronized(lock) {
                if (pageInCache(page) && !page.isDecoded) {
                    page.state = PageState.IDLE
                    queueForDecode(page, prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false)
                } else if (pageInCache(page)) {
                    page.state = PageState.IDLE
                }
            }
            return
        }

        // Transition to LOADING state
        synchronized(lock) {
            if (!pageInCache(page)) return
            page.state = PageState.LOADING
        }

        // Start the download
        if (page.page.status == Page.State.Queue) {
            scope.launch(Dispatchers.IO) {
                loader.loadPage(page.page)
            }
        }

        // Set up progress indicator and re-queue when ready
        scope.launch {
            try {
                val downloadProgressJob = launch {
                    page.page.progressFlow.collect { value ->
                        // Still the placeholder? Evicted or decoded, and there is nothing to fill.
                        val progressPage = synchronized(lock) {
                            if (!pageInCache(page)) return@collect
                            page.imagePage as? ProgressPage ?: return@collect
                        }
                        progressPage.progress = value / 100f
                    }
                }

                page.page.statusFlow.takeWhile { state ->
                    when (state) {
                        Page.State.Queue, Page.State.LoadPage, Page.State.DownloadImage -> true
                        is Page.State.Error -> {
                            logcat(LogPriority.ERROR) { "Page load error: ${state.error}" }
                            false
                        }

                        Page.State.Ready -> false
                    }
                }.collect {}

                downloadProgressJob.cancel()

                // Re-queue for decoding if ready
                synchronized(lock) {
                    if (pageInCache(page) && page.state == PageState.LOADING) {
                        page.state = PageState.IDLE
                        if (page.page.status == Page.State.Ready && !page.isDecoded) {
                            queueForDecode(
                                page,
                                prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "startPageLoad error" }
                synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            }
        }
    }

    private suspend fun decodeReaderPage(page: ViewerReaderPage) {
        // If page isn't downloaded yet, start loading
        if (page.page.status != Page.State.Ready) {
            startPageLoad(page)
            return
        }

        val stream = page.page.stream?.invoke() ?: run {
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            return
        }

        stream.use { input ->
            // Check if still valid before decoding (not evicted and doesn't have decoded image yet)
            synchronized(lock) {
                if (!pageInCache(page) || page.isDecoded) {
                    if (pageInCache(page)) page.state = PageState.IDLE
                    return
                }
            }

            // Buffered to read the spread tag, then decoded from the buffer. On the preference,
            // not isDualPageMode(): WIDE is portrait-off, and a rotate never re-decodes.
            val bytes = if (config.dualPageView != ReaderPreferences.DualPageView.NEVER) {
                input.readBytes()
            } else {
                null
            }

            // Left untouched for a file that names no side - [spreadPosition] then derives one.
            if (bytes != null) {
                val tag = Kim.readMetadata(bytes.inputStream(), bytes.size.toLong())
                    ?.findStringValue(TiffTag.TIFF_TAG_PAGE_NAME)
                page.taggedSpreadPosition = when (tag) {
                    "Left" -> SpreadPosition.LEFT
                    "Right" -> SpreadPosition.RIGHT
                    null -> null
                    else -> SpreadPosition.SINGLE
                }
            }

            val dec = ImageDecoder.new(bytes?.inputStream() ?: input)

            val pageCount = dec.pages

            if (pageCount == 0) throw Exception("No frames decoded")

            val backgroundColor = if (config.automaticBackground) null else readerBackgroundColor()

            val firstFrame = dec.decodeNext()

            val imagePage = if (pageCount == 1) {
                // Only trim when not animated and not in dual page mode
                val trimColors = if (config.imageCropBorders && !isDualPageMode()) {
                    listOf(
                        floatArrayOf(1f, 1f, 1f),
                        floatArrayOf(0f, 0f, 0f),
                    )
                } else {
                    null
                }

                val firstImage = Image(
                    firstFrame.image,
                    firstFrame.width,
                    firstFrame.height,
                    createMipMaps = true,
                    trimColors = trimColors,
                    trimThreshold = 0.15f,
                    backgroundColor = backgroundColor,
                )

                ImagePage.ImageSingle(firstImage)
            } else {
                val frames = ArrayList<Pair<Image, Int>>(pageCount)

                val firstImage = Image(
                    firstFrame.image,
                    firstFrame.width,
                    firstFrame.height,
                    createMipMaps = false,
                    backgroundColor = backgroundColor,
                )

                frames.add(Pair(firstImage, firstFrame.duration))

                repeat(pageCount - 1) {
                    (page.imagePage as? ProgressPage)?.apply {
                        progress = (it + 1).toFloat() / pageCount
                    }
                    val frame = dec.decodeNext()
                    val image = Image(
                        frame.image,
                        frame.width,
                        frame.height,
                        createMipMaps = false,
                        backgroundColor = firstImage.backgroundColor,
                    )
                    frames.add(Pair(image, frame.duration))
                }

                ImagePage.ImageSingle(frames)
            }

            synchronized(lock) {
                if (pageInCache(page) && !page.isDecoded && !page.imagePage.destroyed) {
                    val oldImagePage = page.imagePage
                    page.imagePage = imagePage
                    noteIfLone(page)
                    page.state = PageState.IDLE
                    oldImagePage.cleanup()
                    // Fade up from the placeholder's colour, if that placeholder was on screen -
                    // one that decoded out of view has nothing left to fade from.
                    if (oldImagePage.isOnScreen) imagePage.fadeIn()
                    if (!isDualPageMode()) {
                        (page.imagePage as? ImagePage.ImageSingle)?.let {
                            if (!applyWideZoomIfNeeded(it)) {
                                applyFitModeAnchor(it)
                            }
                        }
                    }
                    pager.state.invalidate()
                } else {
                    if (pageInCache(page)) page.state = PageState.IDLE
                }
            }
        }
    }

    private fun applyWideZoomIfNeeded(page: ImagePage.ImageSingle): Boolean {
        if (!config.landscapeZoom) return false

        val screenW = pager.state.width
        val screenH = pager.state.viewportHeight
        if (screenW <= 0 || screenH <= 0) return false

        // don't zoom if it fits at original scale
        if (page.trimWidth <= screenW) return false

        val image = page.image ?: return false

        // if the untrimmed page is wide and the trimmed page is wide
        val aspectRatio = min(
            page.trimWidth.toFloat() / page.trimHeight.toFloat(),
            image.width.toFloat() / image.height.toFloat(),
        )

        // not wide enough
        if (aspectRatio < 1.1) return false

        // Wide page: half the image width is wider than the screen aspect ratio
        if (aspectRatio <= 2f * screenW.toFloat() / screenH) return false

        // need to set parent for positioning to work
        page.parent = pager.state

        // Scale to fit half the image width to the full screen width
        page.homeScale = screenW.toFloat() / (page.trimWidth / 2f)

        page.scale = page.homeScale

        val minX = page.minX(page.homeScale)
        val maxX = page.maxX(page.homeScale)

        page.x = when (config.imageZoomType) {
            ZoomStartPosition.LEFT -> maxX
            ZoomStartPosition.RIGHT -> minX
            ZoomStartPosition.CENTER -> 0f
        }

        page.y = page.homeY

        return true
    }

    private fun applyFitModeAnchor(page: ImagePage.ImageSingle) {
        val scaleType = config.imageScaleType
        if (scaleType != 3 && scaleType != 4 && scaleType != 5) return

        val screenW = pager.state.width
        val screenH = pager.state.viewportHeight
        if (screenW <= 0 || screenH <= 0) return

        val w = page.trimWidth.toFloat()
        val h = page.trimHeight.toFloat()
        if (w <= 0f || h <= 0f) return

        page.parent = pager.state

        page.homeScale = when (scaleType) {
            3 -> screenW / w
            4 -> screenH / h
            else -> 1f // original size
        }.coerceAtLeast(0.01f)

        page.scale = page.homeScale

        if (scaleType == 5) { // original size
            if (page.homeScale < page.minScale) {
                page.minScale = page.homeScale
            }
        }

        val minX = page.minX(page.homeScale)
        val maxX = page.maxX(page.homeScale)

        page.x = when (config.imageZoomType) {
            ZoomStartPosition.LEFT -> maxX
            ZoomStartPosition.RIGHT -> minX
            ZoomStartPosition.CENTER -> 0f
        }

        page.y = page.homeY
    }

    /**
     * Queue a page for decoding. If prioritize=true, moves existing queued page to front.
     */
    protected fun preloadPage(page: ViewerPage, prioritize: Boolean = false) {
        synchronized(lock) {
            val cachedPage = findInCache(pageKey(page)) ?: return
            if (cachedPage is ViewerReaderPage) {
                queueForDecode(cachedPage, prioritize)
            }
        }
    }

    protected fun preloadPages(page: ViewerPage) {
        // Get the canonical page from cache to ensure we're working with current data
        val key = pageKey(page)
        val cachedPage = synchronized(lock) { findInCache(key) } ?: return

        // Priority order: current (highest), next1, next2, prev1, prev2 (lowest)
        // Add in reverse for LIFO, current page gets prioritized

        // Add prev pages (lowest priority)
        val prevPages = mutableListOf<ViewerPage>()
        var p: ViewerPage? = cachedPage
        for (i in 0 until preloadBehind) {
            p = p?.prev ?: break
            prevPages.add(p)
        }
        prevPages.asReversed().forEach { preloadPage(it) }

        // Add next pages (medium priority)
        val nextPages = mutableListOf<ViewerPage>()
        p = cachedPage
        for (i in 0 until preloadAhead) {
            p = p?.next ?: break
            nextPages.add(p)
        }
        nextPages.asReversed().forEach { preloadPage(it) }

        // Add current spread last with priority flag (highest priority in LIFO)
        // Also preload the paired page
        cachedPage.next?.let { preloadPage(it, prioritize = true) }
        preloadPage(cachedPage, prioritize = true)
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val pages = chapters.currChapter.pages ?: return

        this.viewerChapters = chapters

        val requestedIndex = min(chapters.currChapter.requestedPage, pages.lastIndex)
        val requestedPage = pages[requestedIndex]

        // Get the page and align to spread anchor if needed
        val page = currentPage ?: getPage(requestedPage)
        currentPage = getSpreadAnchor(page)
        (currentPage as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
        preloadPages(currentPage!!)

        pager.state.apply {
            onPageChange = onPageChange@{ delta ->
                // The viewer already showed the page at fetchPage(delta).
                // We need to update currentPage to match that.
                val current = currentPage ?: return@onPageChange

                // Navigate the same way fetchPage does
                var page = current
                val step = if (delta > 0) 1 else -1
                repeat(abs(delta)) {
                    page = nextPage(page, step) ?: return@onPageChange
                }

                // Synchronous, since the viewer walks getPage() from here - stale, and the next
                // scroll step crosses the same boundary again.
                currentPage = page

                // The rest ran here too, on the animation thread under the viewer's scroll lock.
                // Posted in order, so nothing is skipped or reordered - and on this viewer's own
                // MainScope, not the state's: that one dispatches inside the frame callback.
                val settled = page
                this@WebGpuViewer.scope.launch {
                    activity.hideMenu()
                    (settled as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
                    preloadPages(settled)

                    (settled as? ViewerTransitionPage)?.let { transitionPage ->
                        if (transitionPage.prevChapter == null || transitionPage.nextChapter == null) {
                            activity.showMenu()
                        }
                    }
                }
            }

            invalidate()
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     * In dual page mode, aligns to the start of the spread containing the page.
     */
    override fun moveToPage(page: ReaderPage) {
        // Pin first: resolving a target outside the cached window trims the cache.
        pinnedFromPage = currentPage?.let { buildSpreadPage(it) }
        // Get the page and align to spread anchor based on image position
        moveToPage(getSpreadAnchor(getPage(page)))
    }

    private fun moveToPage(newPage: ViewerPage) {
        val previousPage = currentPage
        // Before preloadPages below trims the cache - see [pinnedFromPage].
        val fromSpread = previousPage?.let { buildSpreadPage(it) }
        pinnedFromPage = fromSpread

        currentPage = newPage
        (newPage as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
        preloadPages(newPage)

        (newPage as? ViewerTransitionPage)?.let { ViewerTransitionPage ->
            if (ViewerTransitionPage.prevChapter == null || ViewerTransitionPage.nextChapter == null) {
                activity.showMenu()
            }
        }

        if (previousPage == null) return

        val direction = when (previousPage) {
            is ViewerReaderPage if newPage is ViewerReaderPage -> if (previousPage.page.chapter ==
                newPage.page.chapter
            ) {
                (newPage.page.index - previousPage.page.index).coerceIn(-1, 1)
            } else if (previousPage.page.chapter == newPage.prevChapter) {
                1
            } else {
                -1
            }

            is ViewerTransitionPage if newPage is ViewerReaderPage -> if (previousPage.nextChapter ==
                newPage.page.chapter
            ) {
                1
            } else {
                -1
            }

            is ViewerReaderPage if newPage is ViewerTransitionPage -> if (previousPage.page.chapter ==
                newPage.prevChapter
            ) {
                1
            } else {
                -1
            }

            else -> 0
        }

        if (direction != 0 && fromSpread != null) {
            animateTurn(direction, fromSpread)
        } else {
            pager.state.invalidate()
        }
    }

    /** How a [moveToPage] turn is shown. [direction] is 1 forward through the pages, -1 back. */
    protected open fun animateTurn(direction: Int, fromSpread: ImagePage) {
        pager.state.transitionFromPage = fromSpread
        pager.state.animatePageTurn(if (isReversed) direction else -direction)
    }

    /**
     * Moves to the next page.
     */
    fun moveToNext() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    fun moveToPrevious() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    protected open fun moveRight() {
        pager.state.getPage(0)?.let { page ->
            if (config.navigateToPan) {
                val minX = page.minX(page.scale)
                val maxX = page.maxX(page.scale)
                val currentX = page.animationJob?.let { page.animationTargetX } ?: page.x

                val c = if (isVertical && config.imageZoomType == ZoomStartPosition.RIGHT) -1 else 1
                val x = (currentX - c / page.scale).coerceIn(minX, maxX)

                if (!currentX.closeTo(x)) {
                    page.animateTo(targetX = x, targetY = page.y)
                    return
                }
            }

            navigateSpread(if (isReversed) -1 else 1)
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        pager.state.getPage(0)?.let { page ->
            if (config.navigateToPan) {
                val minX = page.minX(page.scale)
                val maxX = page.maxX(page.scale)
                val currentX = page.animationJob?.isActive?.let { page.animationTargetX } ?: page.x

                val c = if (isVertical && config.imageZoomType == ZoomStartPosition.RIGHT) -1 else 1
                val x = (currentX + c / page.scale).coerceIn(minX, maxX)

                if (!currentX.closeTo(x)) {
                    page.animateTo(targetX = x, targetY = page.y)
                    return
                }
            }

            navigateSpread(if (isReversed) 1 else -1)
        }
    }

    /**
     * Get the target page when navigating by spreads from the given page.
     * @param from Starting page
     * @param direction Positive = forward in page numbers, negative = backward
     * @return Target page or null if navigation not possible
     */
    private fun nextPage(from: ViewerPage, direction: Int): ViewerPage? {
        var page = getSpreadAnchor(from)

        page = if (direction > 0) {
            // Going forward (next spread)
            if (page is ViewerReaderPage && canFormSpread(page)) {
                page.next?.next ?: return null
            } else {
                page.next ?: return null
            }
        } else {
            // Going backward (prev spread)
            page.prev ?: return null
        }

        return getSpreadAnchor(page)
    }

    /**
     * Navigate by spreads from current page.
     * @param direction Positive = forward in page numbers, negative = backward
     */
    private fun navigateSpread(direction: Int) {
        val target = currentPage?.let { nextPage(it, direction) } ?: return
        moveToPage(target)
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected fun moveDown() {
        moveToNext()
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted.xor(isReversed)) moveDown() else moveUp()
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted.xor(isReversed)) moveUp() else moveDown()
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isUp) if (ctrlPressed) moveToNext() else moveRight()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isUp) if (ctrlPressed) moveToPrevious() else moveLeft()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }
}
