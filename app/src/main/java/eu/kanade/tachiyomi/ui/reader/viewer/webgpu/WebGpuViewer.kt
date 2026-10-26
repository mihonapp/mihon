package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.core.content.getSystemService
import ca.mpreg.imagedecoder.ImageDecoder
import ca.mpreg.webgpuviewer.closeTo
import ca.mpreg.webgpuviewer.renderer.GainmapInput
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
import ca.mpreg.webgpuviewer.viewer.AnimationFrame
import ca.mpreg.webgpuviewer.viewer.ImagePage
import ca.mpreg.webgpuviewer.viewer.ImageViewer
import ca.mpreg.webgpuviewer.viewer.ImageViewerContinuousState
import ca.mpreg.webgpuviewer.viewer.ImageViewerState
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.DownloadStream
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.GeneralSettings
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.LongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.PagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.collectChanges
import eu.kanade.tachiyomi.ui.reader.viewer.compose.findContent
import eu.kanade.tachiyomi.ui.reader.viewer.mouseWheelPaging
import eu.kanade.tachiyomi.ui.reader.viewer.position
import eu.kanade.tachiyomi.ui.reader.viewer.processPagedImage
import eu.kanade.tachiyomi.ui.reader.viewer.processStripImage
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import okio.Buffer
import tachiyomi.core.common.util.system.logcat
import java.io.FilterInputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.TreeSet
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

private const val STREAM_CHUNK = 1 shl 18

/** Longest a streaming feed waits before rechecking that the page is still wanted. */
private const val STREAM_WAIT_MS = 1000L

/** Least time between feeds, so a trickle of small reads decodes and uploads in batches. */
private const val STREAM_BATCH_MS = 33L

private const val DECODE_WORKERS = 2

// Shared by every viewer: each thread that uploads gets its own Vulkan command stream, which
// some drivers keep after the thread ends. The core threads stay; an overlap with a closing
// viewer gets extra ones that go after a minute idle.
private fun sharedThreads(core: Int, name: String) =
    ThreadPoolExecutor(core, Int.MAX_VALUE, 60, TimeUnit.SECONDS, SynchronousQueue()) { r ->
        Thread(r, name).apply { isDaemon = true }
    }.asCoroutineDispatcher()

// Dedicated threads for the decode workers to avoid blocking Dispatchers.Default pool; two, so a
// huge page doesn't hold up the ones around it.
private val decodeDispatcher = sharedThreads(DECODE_WORKERS, "WebGpuViewer-Decode")

// Streaming decodes block on the network.
private val streamDispatcher = sharedThreads(1, "WebGpuViewer-Stream")

private val LANDSCAPE_ZOOM_DELAY = 500.milliseconds

/** A still over this many pixels shows a shrunk copy while the whole of it decodes. */
private const val SAMPLED_PREVIEW_ABOVE = 16_000_000L

/** About the size of that shrunk copy. */
private const val SAMPLED_PREVIEW_PIXELS = 4_000_000.0

open class WebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
    val viewerState: ImageViewerState = ImageViewerState(isVertical = isVertical, isReversed = isReversed),
) : Viewer {

    open val isContinuous: Boolean = false

    /** Resolved once: render() asks per frame, and createReaderThemeContext builds a Resources. */
    @Volatile
    private var cachedBackgroundColor: Int? = null

    internal fun readerBackgroundColor(): Int =
        cachedBackgroundColor ?: activity.baseContext.readerBackgroundColor(general.theme)
            .also { cachedBackgroundColor = it }

    @Volatile
    private var cachedThemedContext: Context? = null

    /** The reader's night mode rather than the app's - see [ReaderComposePage]. */
    internal val readerThemedContext: Context
        get() = cachedThemedContext ?: activity.createReaderThemeContext().also { cachedThemedContext = it }

    val downloadManager by lazy { activity.appGraph.downloadManager }

    protected val scope = MainScope()

    // Animations decode as they play, a few at a time, apart from page decodes.
    private val animationDispatcher = Dispatchers.Default.limitedParallelism(2)

    // Guards pageCache, decodeQueue, deferredCleanup and chapterPreloadsInFlight.
    private val lock = Object()

    /** Without it the worker parks in [lock].wait() after [destroy], keeping the activity alive. */
    @Volatile
    private var destroyed = false

    private val pageCache = LinkedHashMap<PageKey, ViewerPage>()


    // Processed LIFO - last added is highest priority.
    private val decodeQueue = ArrayDeque<ViewerReaderPage>()

    // Each page's loader.loadPage call, which suspends until cancelled; cancelling drops its
    // still-queued downloads from the loader. Under [lock].
    private val loadJobs = HashMap<PageKey, Job>()

    // Each page's status watcher, from startPageLoad. Under [lock].
    private val watchJobs = HashMap<PageKey, Job>()

    /**
     * Indices of the pages that take a spread to themselves, by chapter - see [spreadStartIndex].
     * Outlives [pageCache]: every page after one of these depends on it, long since evicted.
     */
    private val loneIndices = HashMap<Long?, TreeSet<Int>>()

    /** Chapters [preloadChapterThenRetry] is already waiting on, by id. */
    private val chapterPreloadsInFlight = HashSet<Long?>()

    /**
     * Which side of a dual-page spread a [ViewerReaderPage] belongs on - app-level bookkeeping
     * for [getSpreadAnchor]/[buildSpreadPage], independent of the decoded image itself.
     */
    internal enum class SpreadPosition { LEFT, RIGHT, SINGLE }

    /** Above this, an untagged page is a spread already, not half of one. */
    private val wideAspect = 1.2f

    /** How far two untagged pages' aspect ratios may differ and still pair. */
    private val pairAspectTolerance = 0.1f

    private sealed class PageKey {
        /** [insert] is the second half of a split page. */
        data class Reader(val chapterId: Long?, val index: Int, val insert: Boolean = false) : PageKey()
        data class Transition(val prevId: Long?, val nextId: Long?) : PageKey()
    }

    private fun pageKey(page: ViewerPage): PageKey = when (page) {
        is ViewerReaderPage -> PageKey.Reader(page.page.chapter.chapter.id, page.page.index, page.page is InsertPage)
        is ViewerTransitionPage -> PageKey.Transition(page.prevChapter?.chapter?.id, page.nextChapter?.chapter?.id)
        else -> PageKey.Transition(null, null)
    }

    private fun findInCache(key: PageKey): ViewerPage? = pageCache[key]

    protected fun viewerPageFor(imagePage: ImagePage): ViewerPage? = synchronized(lock) {
        pageCache.values.firstOrNull { it.imagePage === imagePage }
    }

    /** Check if a page is in the cache by identity. O(1) via key lookup. */
    private fun pageInCache(page: ViewerPage): Boolean = pageCache[pageKey(page)] === page

    /**
     * Queue a page for decoding if not already queued/loading/decoded.
     * If prioritize=true and page is already queued, moves it to front.
     * Must be called while holding lock.
     */
    private fun queueForDecode(page: ViewerReaderPage, prioritize: Boolean = false) {
        // Already has a decoded image, or failed and waits for Retry: decoding again would swap in a fresh,
        // unmeasured error page and shift the strip.
        if (page.isDecoded || page.imagePage is ErrorPage) return

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
                if (prioritize && decodeQueue.remove(page)) {
                    decodeQueue.addLast(page)
                }
            }

            PageState.LOADING, PageState.DECODING -> {}
        }
    }

    init {
        repeat(DECODE_WORKERS) { launchDecodeWorker() }
    }

    private fun launchDecodeWorker() {
        scope.launch(decodeDispatcher) {
            try {
                while (!destroyed) {
                    // Popped, vetted and marked under one acquisition - no window for an eviction.
                    val page = synchronized(lock) {
                        while (decodeQueue.isEmpty()) {
                            if (destroyed) return@launch
                            lock.wait()
                        }
                        val candidate = decodeQueue.removeLast()
                        if (pageInCache(candidate) && !candidate.isDecoded) {
                            candidate.apply { state = PageState.DECODING }
                        } else {
                            if (pageInCache(candidate)) candidate.state = PageState.IDLE
                            null
                        }
                    } ?: continue

                    try {
                        decodeReaderPage(page)
                    } catch (e: CancellationException) {
                        // Caught below, the loop would park in wait() on a dead scope.
                        throw e
                    } catch (e: Throwable) {
                        if (e !is Exception && e !is OutOfMemoryError) throw e
                        logcat(LogPriority.ERROR, e) { "decodeReaderPage: ${e.message}" }
                        synchronized(lock) {
                            if (pageInCache(page) && !page.isDecoded && !page.imagePage.destroyed) {
                                val oldImagePage = page.imagePage
                                page.imagePage = ErrorPage(this@WebGpuViewer, page, e)
                                page.preview = null
                                page.state = PageState.IDLE
                                cleanupImage(oldImagePage)
                                page.imagePage.invalidate()
                            } else {
                                if (pageInCache(page)) page.state = PageState.IDLE
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Scope cancelled with the viewer.
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Decode worker died" }
            }
        }
    }

    private val settingsViewModel = activity.settingsViewModel

    /**
     * Settings every paged viewer uses, like scale mode on images or tap zones, which the long strip uses too.
     */
    val paged: PagedSettings
        get() = settingsViewModel.paged.value

    /**
     * Settings every long strip viewer uses, like crop image borders.
     */
    val longStrip: LongStripSettings
        get() = settingsViewModel.longStrip.value

    /**
     * Settings only the paged WebGPU viewers use, like page transitions, which the long strip uses too.
     */
    val webgpuPaged: WebGpuPagedSettings
        get() = settingsViewModel.webgpuPaged.value

    /**
     * Settings only the long strip WebGPU viewer uses, like the page gap.
     */
    val webgpuLongStrip: WebGpuLongStripSettings
        get() = settingsViewModel.webgpuLongStrip.value

    /**
     * Settings shared by every viewer, like allow taps.
     */
    val general: GeneralSettings
        get() = settingsViewModel.general.value

    val imageZoomType: ZoomStartPosition
        get() = paged.page.zoomStart.position(
            auto = if (isReversed) ZoomStartPosition.RIGHT else ZoomStartPosition.LEFT,
        )

    // Read from the render and decode threads, via the prevChapter/nextChapter getters.
    @Volatile
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

    /** True while [pinnedFromPage] is drawing [image], as itself or as a spread side. */
    private fun isPinnedImage(image: ImagePage): Boolean {
        val pinned = pinnedFromPage ?: return false
        if (pinned === image) return true
        return pinned is ImagePage.ImageSpread && (pinned.left === image || pinned.right === image)
    }

    /** True while [pinnedFromPage] is drawing [page]'s image, as itself or as a spread side. */
    private fun isPinned(page: ViewerPage): Boolean = isPinnedImage(page.imagePage)

    /** Images swapped out while [pinnedFromPage] was still drawing them, i.e. mid page turn. */
    private val deferredCleanup = mutableListOf<ImagePage>()

    /** Cleans up [image], or defers it while [pinnedFromPage] draws it. Call under [lock]. */
    private fun cleanupImage(image: ImagePage) {
        if (isPinnedImage(image)) deferredCleanup.add(image) else image.cleanup()
    }

    /** Releases what [pinnedFromPage] no longer protects. Call under [lock]. */
    private fun flushDeferredCleanup() {
        val iterator = deferredCleanup.iterator()
        while (iterator.hasNext()) {
            val image = iterator.next()
            if (!isPinnedImage(image)) {
                image.cleanup()
                iterator.remove()
            }
        }
    }

    open val preloadAhead = 3
    open val preloadBehind = 2

    /**
     * Everything [preloadPages] reaches, plus slack. Sized exactly, a chapter transition page - or
     * in dual mode a spread partner - evicts a page the next fetch asks for, and it decodes again.
     */
    open val cacheSize get() = 1 + preloadAhead + preloadBehind + if (isDualPageMode()) 3 else 1

    /**
     * What the cached pages' textures may hold, an eighth of the device's memory: [cacheSize] counts pages, and ten
     * pages of 3413x4800 got a 4 GB phone's reader killed.
     */
    private val textureBudget = ActivityManager.MemoryInfo()
        .also { activity.getSystemService<ActivityManager>()?.getMemoryInfo(it) }
        .totalMem / 8

    /** Roughly what [page]'s own image holds on the GPU: the full size plus a third for the smaller levels. */
    private fun textureBytes(page: ViewerPage): Long {
        val single = page.imagePage as? ImagePage.ImageSingle
        val image = single?.takeIf { it.ownsImage }?.image ?: return 0
        return image.width.toLong() * image.height * (if (image.isHdr) 8 else 4) * 4 / 3
    }

    /**
     * Evicts the farthest pages while the cache is over [textureBudget], keeping what is on screen and one page
     * past it. Call under [lock].
     */
    private fun trimToTextureBudget(keep: ViewerPage) {
        if (textureBudget <= 0) return
        while (pageCache.values.sumOf(::textureBytes) > textureBudget) {
            if (pageCache.values.count { !it.imagePage.isOnScreen } <= 1) break
            if (!evictFarthestPage(keep = keep, spareOnScreen = true)) break
        }
    }

    enum class PageState {
        IDLE,
        QUEUED,
        LOADING,
        DECODING,
    }

    /**
     * Evicts the page farthest from reference. Must be called while holding lock.
     *
     * Never evicts [reference], [keep], [currentPage] or what [pinnedFromPage] draws. Returns false
     * when nothing was evictable, so a trim loop stops instead of spinning.
     *
     * @param reference The page to use as reference (defaults to currentPage)
     * @param keep A page about to be handed out: evicted, it would be drawn destroyed.
     * @param spareOnScreen Also never evicts a page on screen, which would only decode again.
     */
    private fun evictFarthestPage(
        reference: ViewerPage? = null,
        keep: ViewerPage? = null,
        spareOnScreen: Boolean = false,
    ): Boolean {
        val current = reference ?: currentPage ?: return false
        val candidates = pageCache.values
            .filter { it !== current && it !== keep && it !== currentPage && !isPinned(it) }
            .filter { !spareOnScreen || !it.imagePage.isOnScreen }
            .toMutableSet()
        if (candidates.isEmpty()) return false

        // Read once - the getter measures the viewport.
        val reach = cacheSize

        fun findNext(page: ViewerPage): ViewerPage? = when (page) {
            is ViewerReaderPage -> {
                val chapterId = page.page.chapter.chapter.id
                val nextIndex = page.page.index + 1
                page.splitInto?.takeIf {
                    page.page !is InsertPage
                }?.let { pageCache[PageKey.Reader(chapterId, it.index, true)] }
                    ?.takeIf { it in candidates } ?: candidates.find {
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
                (page.page as? InsertPage)?.let { pageCache[PageKey.Reader(chapterId, it.index)] }
                    ?.takeIf { it in candidates } ?: candidates.find {
                    it is ViewerReaderPage && it.page.chapter.chapter.id == chapterId && it.page.index == prevIndex &&
                        it.page is InsertPage
                } ?: candidates.find {
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

        for (i in 0 until reach) {
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
        // Through the pin: a decode that swapped this page's image leaves its spread unguarded.
        (toRemove as? ViewerReaderPage)?.spreadPage?.let(::cleanupImage)
        cleanupImage(toRemove.imagePage)
        return true
    }

    /**
     * Gets or creates a page. Thread-safe.
     * @param referencePage The page to use as reference for eviction (defaults to currentPage)
     */
    fun getPage(page: ReaderPage, referencePage: ViewerPage? = null): ViewerPage {
        val key = PageKey.Reader(page.chapter.chapter.id, page.index, page is InsertPage)
        return synchronized(lock) {
            findInCache(key) ?: ViewerReaderPage(page).also { newPage ->
                pageCache[key] = newPage
                val limit = cacheSize
                while (pageCache.size > limit) {
                    if (!evictFarthestPage(referencePage ?: newPage, keep = newPage)) break
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
                val limit = cacheSize
                while (pageCache.size > limit) {
                    if (!evictFarthestPage(referencePage ?: newPage, keep = newPage)) break
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
        // fetchPage reaches prev/next per frame - unguarded, each frame starts another 5s poll.
        val chapterId = chapter.chapter.id
        synchronized(lock) {
            if (!chapterPreloadsInFlight.add(chapterId)) return
        }

        scope.launch(Dispatchers.IO) {
            try {
                activity.viewModel.preload(chapter)
                repeat(25) {
                    if (chapter.state is ReaderChapter.State.Loaded) {
                        currentPage?.let { preloadPages(it) }
                        return@launch
                    }
                    delay(200.milliseconds)
                }
            } finally {
                synchronized(lock) { chapterPreloadsInFlight.remove(chapterId) }
            }
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
        override var imagePage: ImagePage = TransitionPage(this@WebGpuViewer, prevChapter, nextChapter)

        override val prev: ViewerPage?
            get() = prevChapter?.pages?.lastOrNull()?.let { getPage(it, currentPage).secondHalf() }

        override val next: ViewerPage?
            get() = nextChapter?.pages?.firstOrNull()?.let { getPage(it, currentPage) }
    }

    inner class ViewerReaderPage(val page: ReaderPage) : ViewerPage() {
        /** The second half, once decoding split this page in two. */
        @Volatile
        var splitInto: InsertPage? = null

        /** Cached spread ImagePage when this page is the anchor of a dual-page spread */
        var spreadPage: ImagePage.ImageSpread? = null

        /** The side the file names, or null for none. Never a value merely derived from the index. */
        @Volatile
        internal var taggedSpreadPosition: SpreadPosition? = null

        /** The decoded image's shape, or null while this page is still a placeholder. */
        internal val aspectRatio: Float?
            get() = (imagePage as? ImagePage.ImageSingle)?.takeIf { it !is ImagePage.Compose }?.let {
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
            get() = taggedSpreadPosition == SpreadPosition.SINGLE || (aspectRatio ?: 0f) > wideAspect

        override var imagePage: ImagePage = ProgressPage(this@WebGpuViewer)

        /** So a download streams once; weak, as it holds the whole download. Under [lock]. */
        internal var streamedFrom: WeakReference<DownloadStream>? = null

        /** A streaming decode owns the page. Under [lock]. */
        internal var streaming = false

        /** Not counted by [isDecoded], and nor is the reader's chrome, which is always "decoded". */
        @Volatile
        internal var preview: ImagePage? = null

        override val isDecoded
            get() = imagePage.let {
                it !== preview && it !is ImagePage.Compose && (it as? ImagePage.ImageSingle)?.isDecoded == true
            }

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
            get() = (page as? InsertPage)?.let { getPage(it.parent, currentPage) } ?: page.chapter.pages?.let { pages ->
                pages.getOrNull(page.index - 1)?.let { getPage(it, currentPage).secondHalf() } ?: run {
                    val prevChapter = prevChapter ?: return@run getPage(null, page.chapter, currentPage)

                    if (prevChapter.state !is ReaderChapter.State.Loaded) {
                        preloadChapterThenRetry(prevChapter)
                    }

                    if (general.alwaysShowChapterTransition) {
                        getPage(prevChapter, page.chapter, currentPage)
                    } else {
                        prevChapter.pages?.lastOrNull()?.let { getPage(it, currentPage).secondHalf() }
                    }
                }
            }

        override val next: ViewerPage?
            get() = splitInto?.takeIf { page !is InsertPage }?.let { getPage(it, currentPage) }
                ?: page.chapter.pages?.let { pages ->
                    pages.getOrNull(page.index + 1)?.let { getPage(it, currentPage) } ?: run {
                        val nextChapter = nextChapter ?: return@run getPage(page.chapter, null, currentPage)

                        if (nextChapter.state !is ReaderChapter.State.Loaded) {
                            preloadChapterThenRetry(nextChapter)
                        }

                        if (general.alwaysShowChapterTransition) {
                            getPage(page.chapter, nextChapter, currentPage)
                        } else {
                            nextChapter.pages?.firstOrNull()?.let { getPage(it, currentPage) }
                        }
                    }
                }
    }

    private fun ViewerPage.secondHalf(): ViewerPage =
        (this as? ViewerReaderPage)?.splitInto?.let { getPage(it, currentPage) } ?: this

    /**
     * Check if dual page mode is currently active based on settings and view dimensions.
     * Dual page is never active for continuous (scrolling) viewers.
     */
    fun isDualPageMode(): Boolean {
        if (isContinuous) return false
        return when (webgpuPaged.page.dualPageView) {
            ReaderPreferences.DualPageView.NEVER -> false
            ReaderPreferences.DualPageView.ALWAYS -> true
            ReaderPreferences.DualPageView.WIDE -> {
                val width = viewerState.width
                val height = viewerState.height
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
     * Get the anchor page for a spread.
     * RTL: anchor is RIGHT, for LEFT page returns previous RIGHT
     * LTR: anchor is LEFT, for RIGHT page returns previous LEFT
     */
    private fun getSpreadAnchor(page: ViewerPage): ViewerPage {
        if (!isDualPageMode()) return page
        if (page !is ViewerReaderPage) return page

        if (page.spreadPosition == partnerPosition) {
            val prev = page.prev as? ViewerReaderPage ?: return page
            if (prev.page.chapter == page.page.chapter && prev.spreadPosition == anchorPosition &&
                canPairShapes(prev, page)
            ) {
                return prev
            }
        }

        return page
    }

    /** Who [page] pairs with, or null. One verdict for [buildSpreadPage] and [progressPage]. */
    private fun spreadPartner(page: ViewerReaderPage): ViewerReaderPage? {
        if (!isDualPageMode()) return null
        if (page.spreadPosition != anchorPosition) return null
        val next = (page.next as? ViewerReaderPage)?.takeIf { it.page.chapter == page.page.chapter } ?: return null
        return next.takeIf { it.spreadPosition == partnerPosition && canPairShapes(page, it) }
    }

    /**
     * Whether the page [page] would pair with is there but of another shape. Such a page stands alone in the
     * middle; one with nothing to pair with - the cover, a chapter's ends - keeps to its own side.
     */
    private fun refusedForShape(page: ViewerReaderPage): Boolean {
        val other = when (page.spreadPosition) {
            anchorPosition -> page.next
            partnerPosition -> page.prev
            else -> null
        } as? ViewerReaderPage ?: return false
        if (other.page.chapter != page.page.chapter) return false
        val (anchor, partner) = if (page.spreadPosition == anchorPosition) page to other else other to page
        return anchor.spreadPosition == anchorPosition && partner.spreadPosition == partnerPosition &&
            !canPairShapes(anchor, partner)
    }

    /** Page to report progress for - the spread's lastmost page, not the anchor. */
    private fun progressPage(page: ViewerPage): ViewerReaderPage? {
        val readerPage = page as? ViewerReaderPage ?: return null
        return spreadPartner(readerPage) ?: readerPage
    }

    private fun buildSpreadPage(page: ViewerPage): ImagePage {
        if (page !is ViewerReaderPage) {
            return page.imagePage
        }

        if (!isDualPageMode()) {
            return page.imagePage
        }

        // Whatever the page is holding takes its half of the seam, decoded or not:
        // [ImagePage.ImageSpread] draws a [ImagePage.Render] side into its own half. A page left
        // out would take the whole viewport instead, hiding its partner with it.
        val imagePage = page.imagePage

        if (page.spreadPosition == SpreadPosition.SINGLE || refusedForShape(page)) {
            page.spreadPage = null
            return imagePage
        }

        // Null for a partner reaching here directly, which means no anchor before it - a lone
        // RIGHT at a chapter boundary - so it draws alone on its own side.
        val partnerImagePage = spreadPartner(page)?.imagePage

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
        viewerState.apply {
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
                activity.onViewerTap(offset)
            }

            onLongTap = { _ ->
                if (activity.viewModel.state.value.menuVisible || general.longTapEnabled) {
                    (currentPage as? ViewerReaderPage)?.let { activity.onPageLongTap(it.page) }
                }
            }
        }

        // The pages are made with all of these, applied at first only from the WebGPU paged ones every mode reads
        settingsViewModel.general.collectChanges(scope) { old, new ->
            if (old != null && old.theme != new.theme) {
                onPageSettingsChanged()
            }
        }
        settingsViewModel.simplePaged.collectChanges(scope) { old, new ->
            if (old != null && old != new) {
                onPageSettingsChanged()
            }
        }
        settingsViewModel.webgpuPaged.collectChanges(scope) { old, new ->
            if (old?.page != new.page) {
                onPageSettingsChanged()
            }
        }
        if (isContinuous) {
            settingsViewModel.longStrip.collectChanges(scope) { old, _ ->
                if (old != null) {
                    onPageSettingsChanged()
                }
            }
            settingsViewModel.webgpuLongStrip.collectChanges(scope) { old, new ->
                if (old != null && old.page != new.page) {
                    onPageSettingsChanged()
                }
            }
        }
        val common = if (isContinuous) {
            settingsViewModel.webgpuLongStrip.map { it.common }
        } else {
            settingsViewModel.webgpuPaged.map { it.common }
        }
        common.distinctUntilChanged().collectChanges(scope) { old, new ->
            if (old?.doubleTapGesturesDisabled != new.doubleTapGesturesDisabled) {
                viewerState.doubleTapGesturesEnabled = !new.doubleTapGesturesDisabled
            }
            if (old?.imageFilters != new.imageFilters) {
                // Else an older, slower load could land last.
                imageFiltersJob?.cancel()
                imageFiltersJob = scope.launch { viewerState.imageFilters = loadFilters(new.imageFilters) }
            }
            if (old?.tileFilters != new.tileFilters) {
                loadTileFilters(new.tileFilters)
            }
        }
        settingsViewModel.paged.collectChanges(scope) { old, new ->
            if (old != null && old.page != new.page) {
                onPageSettingsChanged()
            }
        }
    }

    /**
     * Applies what the pages are made with and remakes them.
     */
    private fun onPageSettingsChanged() {
        val pageSettings = webgpuPaged.page
        // A theme change comes through here.
        cachedBackgroundColor = null
        cachedThemedContext = null

        val isDual = isDualPageMode()
        viewerState.apply {
            transition = when (if (isDual) pageSettings.transitionAnimationDual else pageSettings.transitionAnimation) {
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

            when (if (isDual) pageSettings.cutoutModeDual else pageSettings.cutoutMode) {
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
                backgroundColor = readerBackgroundColor()
                // Side padding narrows every page as the other long strips' padding does.
                homeScale =
                    min(webgpuLongStrip.page.continuousMinWidth, 100 - 2 * webgpuLongStrip.page.sidePadding) / 100f
                scale = homeScale
                cropBorders = this@WebGpuViewer.cropBorders
                // The other long strips' limits, which their side padding scales with.
                minScale = if (longStrip.zoomOutDisabled) 0f else homeScale * 0.5f
                maxScale = homeScale * 3f

                (this@WebGpuViewer as? WebGpuViewerContinuous)?.let {
                    if (this@WebGpuViewer.useGap) {
                        pageGap = webgpuLongStrip.page.continuousGap / 100f
                    }
                }
            }
        }

        synchronized(lock) {
            decodeQueue.clear()
            loadJobs.values.forEach { it.cancel() }
            loadJobs.clear()
            watchJobs.values.forEach { it.cancel() }
            watchJobs.clear()
            pageCache.values.forEach {
                it.state = PageState.IDLE
                (it as? ViewerReaderPage)?.spreadPage?.let(::cleanupImage)
                cleanupImage(it.imagePage)
            }
            pageCache.clear()

            currentPage =
                (currentPage as? ViewerReaderPage)?.page?.let { getPage((it as? InsertPage)?.parent ?: it) }
                    ?: (currentPage as? ViewerTransitionPage)?.let {
                        getPage(it.prevChapter, it.nextChapter)
                    }

            currentPage?.let { preloadPages(it) }
        }

        viewerState.invalidate()
    }

    private var imageFiltersJob: Job? = null
    private var tileFiltersJob: Job? = null

    private fun loadTileFilters(shaders: List<WebGpuShader>) {
        tileFiltersJob?.cancel()
        tileFiltersJob = scope.launch { viewerState.tileFilters = loadFilters(shaders) }
    }

    /** Fresh instances: one belongs to one viewer and one chain. Skips any that fail to load. */
    private suspend fun loadFilters(shaders: List<WebGpuShader>) = withContext(Dispatchers.IO) {
        shaders.mapNotNull { shader ->
            try {
                WebGpuShaders.load(activity, shader)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "filter ${shader.name}" }
                null
            }
        }
    }

    override fun destroy() {
        // Taken mid-decode, only the flag stops the worker parking; the notifyAll below wakes the rest.
        destroyed = true
        scope.cancel()

        synchronized(lock) {
            decodeQueue.clear()
            // Nothing can still be animating, so the pin has nothing left to protect.
            pinnedFromPage = null
            pageCache.values.forEach {
                it.state = PageState.IDLE
                (it as? ViewerReaderPage)?.spreadPage?.cleanup()
                it.imagePage.cleanup()
            }
            pageCache.clear()
            deferredCleanup.forEach { it.cleanup() }
            deferredCleanup.clear()
            loneIndices.clear()
            chapterPreloadsInFlight.clear()
            lock.notifyAll()
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        ImageViewer(modifier = modifier.mouseWheelPaging(this), state = viewerState)
    }

    private val cropBorders: Boolean
        get() = if (isContinuous) longStrip.cropBorders else paged.page.cropBorders

    /**
     * Asks the loader for [page], after whatever it was asked for before; a request replaces
     * the page's last one. Cancelling it drops the download unless it has started.
     */
    private fun requestDownload(page: ViewerReaderPage) {
        val loader = page.page.chapter.pageLoader ?: return
        if (page.page.downloadStream == null) page.page.downloadStream = DownloadStream()
        synchronized(lock) {
            loadJobs.remove(pageKey(page))?.cancel()
            // Undispatched for the HTTP loader, which queues before suspending, so requests queue in
            // order. Other loaders may work first, which mustn't happen here under the lock.
            val start = if (loader is HttpPageLoader) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT
            loadJobs[pageKey(page)] = scope.launch(Dispatchers.IO, start = start) {
                loader.loadPage(page.page)
            }
        }
    }

    /** [ErrorPage]'s Retry: back to the ring, then loaded and decoded afresh. */
    internal fun retryLoad(page: ViewerReaderPage) {
        synchronized(lock) {
            if (!pageInCache(page) || page.imagePage !is ErrorPage) return
            val old = page.imagePage
            page.imagePage = ProgressPage(this)
            cleanupImage(old)
            if (page.state != PageState.LOADING) page.state = PageState.IDLE
        }
        page.page.chapter.pageLoader?.retryPage(page.page)
        synchronized(lock) {
            if (pageInCache(page)) queueForDecode(page, prioritize = true)
        }
        viewerState.invalidate()
    }

    /** Downloads [page] if needed, then re-queues it for decode once ready. */
    private fun startPageLoad(page: ViewerReaderPage) {
        val loader = page.page.chapter.pageLoader ?: run {
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            return
        }

        if (page.page.status == Page.State.Ready) {
            synchronized(lock) {
                if (pageInCache(page) && !page.isDecoded && !page.streaming) {
                    page.state = PageState.IDLE
                    queueForDecode(page, prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false)
                } else if (pageInCache(page)) {
                    page.state = PageState.IDLE
                }
            }
            return
        }

        synchronized(lock) {
            if (!pageInCache(page)) return
            page.state = PageState.LOADING
        }

        if (page.page.status == Page.State.Queue) {
            requestDownload(page)
        }

        val watch = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val downloadProgressJob = launch {
                    page.page.progressFlow.collect { value ->
                        // Set under the lookup's lock, or an eviction's cleanup() lands between.
                        synchronized(lock) {
                            if (!pageInCache(page)) return@collect
                            (page.imagePage as? ProgressPage)?.progress = value / 100f
                        }
                    }
                }

                page.page.statusFlow.takeWhile { state ->
                    // Evicted: stop watching, rather than holding the page until the download ends.
                    if (!synchronized(lock) { pageInCache(page) }) return@takeWhile false

                    when (state) {
                        Page.State.Queue, Page.State.LoadPage -> true
                        Page.State.DownloadImage -> {
                            page.page.downloadStream?.let { startStreamDecode(page, it) }
                            true
                        }

                        is Page.State.Error -> {
                            logcat(LogPriority.ERROR) { "Page load error: ${state.error}" }
                            false
                        }

                        Page.State.Ready -> false
                    }
                }.collect {}

                downloadProgressJob.cancel()

                synchronized(lock) {
                    val status = page.page.status
                    if (pageInCache(page) && status is Page.State.Error && !page.streaming) {
                        val old = page.imagePage
                        if (old is ProgressPage) {
                            page.imagePage = ErrorPage(this@WebGpuViewer, page, status.error)
                            cleanupImage(old)
                            viewerState.invalidate()
                        }
                    }
                    if (pageInCache(page) && page.state == PageState.LOADING) {
                        page.state = PageState.IDLE
                        if (page.page.status == Page.State.Ready && !page.isDecoded && !page.streaming) {
                            queueForDecode(
                                page,
                                prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Reset or destroy; whoever cancelled has already put the state right.
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "startPageLoad error" }
                synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            } finally {
                val self = coroutineContext[Job]
                synchronized(lock) {
                    if (watchJobs[pageKey(page)] === self) watchJobs.remove(pageKey(page))
                }
            }
        }
        synchronized(lock) {
            watchJobs.remove(pageKey(page))?.cancel()
            watchJobs[pageKey(page)] = watch
        }
        watch.start()
    }

    private suspend fun decodeReaderPage(page: ViewerReaderPage) {
        if (page.page.status != Page.State.Ready) {
            startPageLoad(page)
            return
        }

        val stream = page.page.stream?.invoke() ?: run {
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            return
        }

        stream.use { input ->
            synchronized(lock) {
                if (!pageInCache(page) || page.isDecoded || page.streaming) {
                    if (pageInCache(page)) page.state = PageState.IDLE
                    return
                }
            }

            logcat(LogPriority.INFO) { "Decoding ${pageName(page)}" }
            val started = SystemClock.uptimeMillis()
            var done = false
            try {
                val dec = ImageDecoder.open(processed(page, input))
                // Cropped as the other readers do, not just framed by the renderer's trim, which draws the margins
                // it pans past. Border detection reads 8-bit RGBA, so HDR keeps the trim.
                val content = try {
                    dec.takeIf { cropBorders && !isDualPageMode() && !it.isHdr && !it.isAnimated }
                        ?.findContent()?.toAndroidRect()
                } catch (e: Throwable) {
                    dec.close()
                    throw e
                }
                buildPage(page, dec, streaming = false, content) { false }
                done = true
            } finally {
                val ms = SystemClock.uptimeMillis() - started
                logcat(LogPriority.INFO) {
                    "${if (done) "Decoded" else "Failed decoding"} ${pageName(page)} in $ms ms"
                }
            }
        }
    }

    /** [input] as the other viewers show it: a wide page rotated to fit, or split with its other half inserted. */
    private fun processed(page: ViewerReaderPage, input: InputStream): InputStream {
        val dualPage = settingsViewModel.simplePaged.value
        if (!dualPage.dualPageRotateToFit && !dualPage.dualPageSplit) return input
        val buffer = Buffer().readFrom(input)
        return when {
            isContinuous -> dualPage.processStripImage(buffer)
            isDualPageMode() -> buffer
            else -> dualPage.processPagedImage(page.page, buffer, !isReversed && !isVertical) { insert ->
                synchronized(lock) { if (pageInCache(page)) page.splitInto = insert }
                currentPage?.let { preloadPages(it) }
            }
        }.inputStream()
    }

    private fun pageName(page: ViewerReaderPage): String {
        val p = page.page
        return p.imageUrl?.takeIf { it.isNotEmpty() }
            ?: p.url.takeIf { it.isNotEmpty() }
            ?: "${p.chapter.chapter.name} page ${p.index + 1}"
    }

    /** Decodes [page] while it downloads; on failure the whole-file decode takes over. */
    private fun startStreamDecode(page: ViewerReaderPage, live: DownloadStream) {
        // Rotating and splitting need the whole file.
        val dualPage = settingsViewModel.simplePaged.value
        if (dualPage.dualPageRotateToFit || dualPage.dualPageSplit) return
        synchronized(lock) {
            if (!pageInCache(page) || page.isDecoded || page.streamedFrom?.get() === live) return
            page.streamedFrom = WeakReference(live)
        }
        scope.launch(streamDispatcher) {
            val claimed = synchronized(lock) {
                val ok = pageInCache(page) && !page.isDecoded && page.page.status == Page.State.DownloadImage
                if (ok) page.streaming = true
                ok
            }
            if (!claimed) return@launch
            // Paced by the download, so this is mostly how long that took.
            val started = SystemClock.uptimeMillis()
            try {
                live.reader().use { reader ->
                    // available() 0 stops the read after the header.
                    val headerOnly = object : FilterInputStream(reader) {
                        override fun available() = 0
                    }
                    logcat(LogPriority.INFO) { "Decoding while downloading ${pageName(page)}" }
                    val chunk = ByteArray(STREAM_CHUNK)
                    ImageDecoder.open(headerOnly, complete = false).let { dec ->
                        var fed = false
                        var fedAt = 0L
                        // Blocks for new bytes: decoding again on none only repeats work.
                        buildPage(page, dec, streaming = true) {
                            if (fed) return@buildPage false
                            while (reader.available() == 0 && !reader.ended) {
                                if (!synchronized(lock) { pageInCache(page) }) return@buildPage false
                                reader.await(1, STREAM_WAIT_MS)
                            }
                            val gap = fedAt + STREAM_BATCH_MS - SystemClock.uptimeMillis()
                            if (gap > 0) reader.await(Int.MAX_VALUE, gap)
                            while (reader.available() > 0) dec.pushData(chunk, 0, reader.read(chunk))
                            fedAt = SystemClock.uptimeMillis()
                            if (reader.ended) {
                                dec.markComplete()
                                fed = true
                            }
                            true
                        }
                    }
                }
                logcat(LogPriority.INFO) {
                    "Decoded while downloading ${pageName(page)} in ${SystemClock.uptimeMillis() - started} ms"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) {
                    "Streaming decode gave up after ${SystemClock.uptimeMillis() - started} ms: ${e.message}"
                }
            } catch (e: OutOfMemoryError) {
                logcat(LogPriority.WARN) { "Streaming decode ran out of memory" }
            } finally {
                synchronized(lock) {
                    page.streaming = false
                    // Failed download: the error, with Retry.
                    val preview = page.preview
                    val status = page.page.status
                    if (pageInCache(page) && page.imagePage === preview && status is Page.State.Error) {
                        page.preview = null
                        page.imagePage = ErrorPage(this@WebGpuViewer, page, status.error)
                        cleanupImage(preview)
                        viewerState.invalidate()
                    }
                    if (pageInCache(page) && !page.isDecoded && page.state == PageState.IDLE &&
                        page.page.status == Page.State.Ready
                    ) {
                        queueForDecode(page, prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false)
                    }
                }
            }
        }
    }

    /**
     * Decodes [dec] into [page], then closes it - unless an animation keeps it, to decode frames
     * past the first as they come due. [feed] pushes more bytes, false once all are in.
     */
    private suspend fun buildPage(
        page: ViewerReaderPage,
        dec: ImageDecoder,
        streaming: Boolean,
        content: Rect? = null,
        feed: () -> Boolean,
    ) {
        var kept = false
        try {
            decodeInto(page, dec, streaming, content, feed) { kept = true }
        } finally {
            if (!kept) dec.close()
        }
    }

    private suspend fun decodeInto(
        page: ViewerReaderPage,
        dec: ImageDecoder,
        streaming: Boolean,
        content: Rect?,
        feed: () -> Boolean,
        keep: () -> Unit,
    ) {
        // The decoder hands the map over unapplied - see ImageDecoder.Gainmap - because how
        // much of it to use depends on the display, so the viewer applies it.
        fun ImageDecoder.Gainmap.toInput() = let {
            GainmapInput(
                pixels = it.pixels,
                width = it.width,
                height = it.height,
                channels = it.channels,
                gamma = it.gamma,
                minContentBoost = it.minContentBoost,
                maxContentBoost = it.maxContentBoost,
                offsetSdr = it.offsetSdr,
                offsetHdr = it.offsetHdr,
            )
        }

        if (isDualPageMode()) {
            page.taggedSpreadPosition = when (dec.getTag("PageName")) {
                "Left" -> SpreadPosition.LEFT
                "Right" -> SpreadPosition.RIGHT
                null -> null
                else -> SpreadPosition.SINGLE
            }
        }

        val backgroundColor = if (general.automaticBackground) null else readerBackgroundColor()

        // SDR only: HDR skips Image's tone mapping; a gain map may need float textures.
        var previewing = streaming && !dec.isHdr && dec.hdrKind != ImageDecoder.HdrKind.GAINMAP

        // Past a sampled decode, decodeNext would look for a next frame.
        var sampled = false

        // Null once the page is unwanted.
        suspend fun nextWhole(onPartial: (suspend (ImageDecoder.Frame) -> Unit)? = null): ImageDecoder.Frame? {
            while (true) {
                if (!synchronized(lock) { pageInCache(page) }) return null
                val f = try {
                    if (sampled) dec.decode() else dec.decodeNext()
                } catch (e: ImageDecoder.NeedMoreDataException) {
                    if (!feed()) throw e
                    continue
                }
                if (!f.partial) return f
                // A partial views the decoder's canvas, valid only until the next step.
                f.use { if (onPartial != null && previewing) onPartial(it) }
                feed()
            }
        }

        var preview: ImagePage.ImageSingle? = null

        // Replaces the placeholder, then updates its texture in place.
        suspend fun showPartial(f: ImageDecoder.Frame) {
            if (!previewing || !f.changed) return
            val shown = preview
            if (shown == null) {
                val single = ImagePage.ImageSingle(
                    Image(
                        f.image,
                        f.width,
                        f.height,
                        createMipMaps = false,
                        backgroundColor = backgroundColor ?: readerBackgroundColor(),
                    ),
                ).apply {
                    // The tile cache would go stale.
                    highQuality = false
                }
                val installed = synchronized(lock) {
                    val old = page.imagePage
                    (pageInCache(page) && old is ProgressPage && !old.destroyed).also { ok ->
                        if (!ok) return@also
                        page.preview = single
                        page.imagePage = single
                        cleanupImage(old)
                        if (old.isOnScreen) single.fadeIn()
                        if (!isDualPageMode()) applyFitModeAnchor(single)
                        viewerState.invalidate()
                    }
                }
                if (installed) preview = single else single.cleanup()
                previewing = installed
            } else {
                // Replaced, as by an error page.
                if (synchronized(lock) { page.imagePage !== shown } || shown.destroyed) {
                    previewing = false
                    return
                }
                val rect = Rect(f.dirtyX, f.dirtyY, f.dirtyX + f.dirtyWidth, f.dirtyY + f.dirtyHeight)
                shown.image?.update(f.image, rect)
                shown.invalidate()
                // A spread's page isn't on screen itself.
                viewerState.invalidate()
            }
        }

        // Nothing to preview: the whole-file decode after the download does as well,
        // without holding the one streaming thread through it.
        if (streaming && !previewing) return
        if (!streaming && !dec.isHdr && dec.hdrKind != ImageDecoder.HdrKind.GAINMAP && !dec.isAnimated) {
            val width = content?.width() ?: dec.width
            val height = content?.height() ?: dec.height
            if (width.toLong() * height > SAMPLED_PREVIEW_ABOVE) {
                sampled = true
                showSampled(page, dec, content, width, height, backgroundColor)
            }
        }
        val firstFrame = nextWhole(onPartial = { showPartial(it) }) ?: return
        // The decoder's own region decode lays a JPEG's rows out wrong, so the content is copied out.
        val firstPixels = content?.let { firstFrame.cropped(it) } ?: firstFrame.image
        var secondFrame: ImageDecoder.Frame? = null
        // A new image until a page takes it.
        var orphan: Image? = null
        // Whole frames hold native pixels until closed, on every exit.
        try {
            // Streamed, a still's end and gain map are only known once all is in.
            if (streaming) {
                while (feed()) {
                    if (!synchronized(lock) { pageInCache(page) }) return
                }
                if (dec.hasNext) {
                    secondFrame = try {
                        nextWhole() ?: return
                    } catch (e: ImageDecoder.DecodeException) {
                        logcat(LogPriority.DEBUG) { "Treating as a still: ${e.message}" }
                        null
                    }
                }
            }
            // decode() for a sampled still leaves hasNext set, as only decodeNext() finds the end.
            val animated = if (streaming) secondFrame != null else !sampled && dec.hasNext

            // A gain map belongs to a still; its headroom is the map's, not the decoder's.
            val gainmap = if (!animated && dec.hdrKind == ImageDecoder.HdrKind.GAINMAP) {
                dec.getGainmap()
            } else {
                null
            }
            val hdrHeadroom = gainmap?.headroomStops ?: dec.hdrHeadroom

            // Only trim when not animated and not in dual page mode
            val trimColors = if (content == null && !animated && cropBorders && !isDualPageMode()) {
                listOf(
                    floatArrayOf(1f, 1f, 1f),
                    floatArrayOf(0f, 0f, 0f),
                )
            } else {
                null
            }

            // The preview's textures become the image, or frame 0; one since replaced or freed
            // (a settings reset) leaves a fresh image to install instead.
            val shown = preview?.takeIf { synchronized(lock) { page.imagePage === it && !it.destroyed } }
            // Only a page the reader hasn't moved re-homes for its trim.
            val wasHome = shown?.atHome == true
            val firstImage = shown?.image?.also {
                it.update(firstFrame.image)
                if (!animated) it.createMipMaps(firstFrame.image)
                it.measure(firstFrame.image, trimColors, trimThreshold = 0.15f, backgroundColor = backgroundColor)
            } ?: Image(
                firstPixels,
                content?.width() ?: firstFrame.width,
                content?.height() ?: firstFrame.height,
                createMipMaps = !animated,
                trimColors = trimColors,
                trimThreshold = 0.15f,
                backgroundColor = backgroundColor,
                hdr = dec.isHdr,
                hdrHeadroom = hdrHeadroom,
                gainmap = gainmap?.toInput(),
            ).also { orphan = it }
            val firstDuration = firstFrame.duration
            firstFrame.close()

            synchronized(lock) {
                val target = when {
                    // Evicted.
                    !pageInCache(page) -> null

                    // The preview becomes the image in place, keeping pan and zoom - unless
                    // something replaced it meanwhile.
                    shown != null -> shown.takeIf { page.imagePage === it && !it.destroyed }?.also {
                        page.preview = null
                        // Tiles for a still; an animation swaps images every frame.
                        it.highQuality = !animated
                        if (wasHome && firstImage.trim != null && !isDualPageMode()) rehome(it)
                        // Bumps frameVersion, so a transition's cached render of the preview is stale.
                        it.invalidate()
                    }

                    // A new page in the placeholder's place.
                    !page.isDecoded && !page.imagePage.destroyed -> {
                        val imagePage = ImagePage.ImageSingle(firstImage)
                        orphan = null
                        val old = page.imagePage
                        // A failed stream's preview is already showing.
                        val fromPreview = old === page.preview
                        // Where the reader took a sampled preview, at the whole image's size. Only one on screen was
                        // laid out, so only its place is the reader's.
                        val moved = (old as? ImagePage.ImageSingle)?.takeIf {
                            fromPreview && it.isOnScreen && it.width > 0 && !it.atHome
                        }?.let { floatArrayOf(it.x, it.y, it.scale, imagePage.width.toFloat() / it.width) }
                        page.imagePage = imagePage
                        page.preview = null
                        cleanupImage(old)
                        // Fade up from the placeholder's colour, if that placeholder was on screen -
                        // one that decoded out of view has nothing left to fade from.
                        if (old.isOnScreen && !fromPreview) imagePage.fadeIn()
                        if (!isDualPageMode()) applyFitModeAnchor(imagePage)
                        moved?.let { (x, y, scale, ratio) -> imagePage.setPos(x * ratio, y * ratio, scale / ratio) }
                        imagePage
                    }

                    else -> null
                }
                if (pageInCache(page)) page.state = PageState.IDLE
                if (target != null) trimToTextureBudget(page)
                target?.also { installedPage ->
                    if (animated) {
                        // The page takes the decoder, which loops, and the second frame decoded
                        // to tell it's animated, which goes first: the rest decode as they're due.
                        keep()
                        var ahead = secondFrame
                        secondFrame = null
                        val name = pageName(page)
                        val release = {
                            ahead?.close()
                            ahead = null
                            dec.close()
                        }
                        installedPage.animate(firstDuration, release) {
                            withContext(animationDispatcher) {
                                val f = ahead?.also { ahead = null } ?: if (dec.hasNext) {
                                    val started = SystemClock.uptimeMillis()
                                    dec.decodeNext().also {
                                        logcat(LogPriority.DEBUG) {
                                            "Decoded frame ${it.index} of $name in " +
                                                "${SystemClock.uptimeMillis() - started} ms"
                                        }
                                    }
                                } else {
                                    null
                                }
                                f?.let { AnimationFrame(it.image, it.duration) { it.close() } }
                            }
                        }
                    }
                    noteIfLone(page)
                    viewerState.invalidate()
                }
            }
        } finally {
            firstFrame.close()
            secondFrame?.close()
            // Nothing took it, normally or not. A preview's image belongs to its page either way.
            orphan?.let { ImagePage.ImageSingle(it).cleanup() }
        }
    }

    /** Puts a shrunk decode of [content] (default: all) in [page]'s placeholder, as the other readers' first tiles. */
    private suspend fun showSampled(
        page: ViewerReaderPage,
        dec: ImageDecoder,
        content: Rect?,
        width: Int,
        height: Int,
        backgroundColor: Int?,
    ) {
        val sample = ceil(sqrt(width.toDouble() * height / SAMPLED_PREVIEW_PIXELS)).toInt()
        // Revisit: averaging while sampling (imagedecoder's decoder-experiments-sampling branch) keeps screentone in
        // this preview from turning into moiré; left out of imagedecoder 18.
        val image = dec.decode(null, sample).use { f ->
            if (!synchronized(lock) { pageInCache(page) }) return
            val rect = content?.let {
                Rect(it.left / sample, it.top / sample, it.right / sample, it.bottom / sample)
                    .apply { intersect(0, 0, f.width, f.height) }
            }
            Image(
                rect?.let { f.cropped(it) } ?: f.image,
                rect?.width() ?: f.width,
                rect?.height() ?: f.height,
                backgroundColor = backgroundColor ?: readerBackgroundColor(),
            )
        }
        val single = ImagePage.ImageSingle(image)
        val installed = synchronized(lock) {
            val old = page.imagePage
            (pageInCache(page) && old is ProgressPage && !old.destroyed).also { ok ->
                if (!ok) return@also
                page.preview = single
                page.imagePage = single
                cleanupImage(old)
                if (old.isOnScreen) single.fadeIn()
                if (!isDualPageMode()) applyFitModeAnchor(single)
                viewerState.invalidate()
            }
        }
        if (!installed) single.cleanup()
    }

    /** [rect] of this frame's RGBA, in a buffer of its own. */
    private fun ImageDecoder.Frame.cropped(rect: Rect): ByteBuffer {
        val rowBytes = rect.width() * 4
        val out = ByteBuffer.allocateDirect(rowBytes * rect.height())
        val src = image.duplicate()
        for (y in rect.top until rect.bottom) {
            val start = (y * width + rect.left) * 4
            src.limit(start + rowBytes).position(start)
            out.put(src)
        }
        return out.apply { flip() }
    }

    /** Animates [page] to where a fresh install would place it, e.g. once its trim is known. */
    private fun rehome(page: ImagePage.ImageSingle) {
        val fromX = page.x
        val fromY = page.y
        val fromScale = page.scale
        // Cached from the old size.
        page.homeScale = -1f
        page.homeX = 0f
        page.homeY = 0f
        page.minScale = 0f
        // The placement a fresh install gets, read back and undone so it can animate there.
        page.x = page.homeX
        page.y = page.homeY
        page.scale = page.homeScale
        applyFitModeAnchor(page)
        val toX = page.x
        val toY = page.y
        val toScale = page.scale
        page.x = fromX
        page.y = fromY
        page.scale = fromScale
        page.animateTo(targetX = toX, targetY = toY, targetScale = toScale)
    }

    /**
     * Unzooms the pages more than one from [current], as the other readers drop all but its neighbours: coming back
     * to one starts it fitted.
     */
    private fun unzoomFarPages(current: ViewerPage) {
        if (isContinuous || isDualPageMode()) return
        val at = (current as? ViewerReaderPage)?.page
        val far = synchronized(lock) {
            pageCache.values.filter {
                val page = (it as? ViewerReaderPage)?.page ?: return@filter false
                at == null || page.chapter != at.chapter || abs(page.index - at.index) > 1
            }.mapNotNull { (it.imagePage as? ImagePage.ImageSingle)?.takeIf { image -> !image.atHome } }
        }
        for (image in far) {
            if (image.isOnScreen) continue
            if (paged.page.imageScaleType != ReaderPreferences.ScaleType.FIT_SCREEN) {
                applyFitModeAnchor(image)
            } else {
                image.home()
            }
        }
    }

    private var landscapeZoomJob: Job? = null

    /**
     * Zooms a wide [page] fitted to the screen to the screen's height half a second after the reader lands on it,
     * from the edge it came [forward] from, as the other readers do.
     */
    private fun landscapeZoom(page: ViewerPage, forward: Boolean) {
        landscapeZoomJob?.cancel()
        if (
            !paged.page.landscapeZoom ||
            paged.page.imageScaleType != ReaderPreferences.ScaleType.FIT_SCREEN ||
            isContinuous ||
            isDualPageMode()
        ) {
            return
        }
        landscapeZoomJob = scope.launch {
            // Waits out the decode, as long as the reader stays.
            while (!page.isDecoded) {
                if (currentPage !== page) return@launch
                delay(100.milliseconds)
            }
            delay(LANDSCAPE_ZOOM_DELAY)
            val image = page.imagePage as? ImagePage.ImageSingle ?: return@launch
            if (currentPage !== page || !image.atHome || image.trimWidth <= image.trimHeight) return@launch
            val screenH = viewerState.viewportHeight
            if (screenH <= 0) return@launch
            val scale = screenH.toFloat() / image.trimHeight
            val x = when (imageZoomType) {
                ZoomStartPosition.LEFT -> if (forward) image.maxX(scale) else image.minX(scale)
                ZoomStartPosition.RIGHT -> if (forward) image.minX(scale) else image.maxX(scale)
                ZoomStartPosition.CENTER -> 0f
            }
            image.animateTo(targetX = x, targetY = image.homeY, targetScale = scale)
        }
    }

    private fun applyFitModeAnchor(page: ImagePage.ImageSingle) {
        val scaleType = paged.page.imageScaleType
        if (scaleType == ReaderPreferences.ScaleType.FIT_SCREEN) return

        val screenW = viewerState.width
        val screenH = viewerState.viewportHeight
        if (screenW <= 0 || screenH <= 0) return

        val w = page.trimWidth.toFloat()
        val h = page.trimHeight.toFloat()
        if (w <= 0f || h <= 0f) return

        page.parent = viewerState

        page.homeScale = when (scaleType) {
            // Stretch: the page covers the screen, as the other readers' centre crop.
            ReaderPreferences.ScaleType.STRETCH -> maxOf(screenW / w, screenH / h)
            ReaderPreferences.ScaleType.FIT_WIDTH -> screenW / w
            ReaderPreferences.ScaleType.FIT_HEIGHT -> screenH / h
            // Smart fit: a tall page's width, a wide page's height.
            ReaderPreferences.ScaleType.SMART_FIT -> if (h > w) screenW / w else screenH / h
            else -> 1f // original size
        }.coerceAtLeast(0.01f)

        page.scale = page.homeScale

        if (scaleType == ReaderPreferences.ScaleType.ORIGINAL_SIZE) {
            if (page.homeScale < page.minScale) {
                page.minScale = page.homeScale
            }
        }

        val minX = page.minX(page.homeScale)
        val maxX = page.maxX(page.homeScale)

        page.x = when (imageZoomType) {
            ZoomStartPosition.LEFT -> maxX
            ZoomStartPosition.RIGHT -> minX
            ZoomStartPosition.CENTER -> 0f
        }

        page.y = page.homeY
    }

    protected fun preloadPage(page: ViewerPage, prioritize: Boolean = false) {
        synchronized(lock) {
            val cachedPage = findInCache(pageKey(page)) ?: return
            if (cachedPage is ViewerReaderPage) {
                queueForDecode(cachedPage, prioritize)
            }
        }
    }

    /**
     * Drops decodes outside [window], and every download not yet started so that [preloadPages]
     * asks again in order. A download already running finishes; its page decodes if it's back
     * in the window by then.
     */
    private fun resetQueues(window: Set<PageKey>) {
        synchronized(lock) {
            val stale = decodeQueue.filter { pageKey(it) !in window }
            decodeQueue.removeAll(stale.toSet())
            stale.forEach { if (it.state == PageState.QUEUED) it.state = PageState.IDLE }

            // All of them: what stays wanted is asked again, in this window's order.
            val iterator = loadJobs.entries.iterator()
            while (iterator.hasNext()) {
                val (key, job) = iterator.next()
                job.cancel()
                iterator.remove()
                if (key in window) continue
                // Never started: its watcher goes too. One downloading keeps it, to decode when done.
                (pageCache[key] as? ViewerReaderPage)?.let {
                    if (it.state == PageState.LOADING && it.page.status == Page.State.Queue) {
                        watchJobs.remove(key)?.cancel()
                        it.state = PageState.IDLE
                    }
                }
            }
        }
    }

    /**
     * Starts [page]'s download now, rather than when a decode worker reaches it - which a
     * slow decode ahead of it holds up, and which left pages past the loader's own preload
     * waiting. Ready then queues the decode.
     */
    private fun startDownload(page: ViewerPage) {
        synchronized(lock) {
            val cachedPage = findInCache(pageKey(page)) as? ViewerReaderPage ?: return
            if (cachedPage.page.status == Page.State.Ready || cachedPage.isDecoded) return
            when (cachedPage.state) {
                // Watched already: only re-request, in this order.
                PageState.LOADING -> {
                    if (cachedPage.page.status == Page.State.Queue) requestDownload(cachedPage)
                    return
                }

                PageState.DECODING -> return
                // Ready requeues it, once downloaded.
                PageState.QUEUED -> decodeQueue.remove(cachedPage)
                PageState.IDLE -> {}
            }
            cachedPage.state = PageState.IDLE
            startPageLoad(cachedPage)
        }
    }

    protected fun preloadPages(page: ViewerPage) {
        // page may be a stale copy - resolve the live cache entry.
        val key = pageKey(page)
        val cachedPage = synchronized(lock) { findInCache(key) } ?: return

        // prev, then next, then current+partner - the last prioritized call ends up highest.
        val prevPages = mutableListOf<ViewerPage>()
        var p: ViewerPage? = cachedPage
        for (i in 0 until preloadBehind) {
            p = p?.prev ?: break
            prevPages.add(p)
        }
        prevPages.asReversed().forEach { preloadPage(it) }

        val nextPages = mutableListOf<ViewerPage>()
        p = cachedPage
        for (i in 0 until preloadAhead) {
            p = p?.next ?: break
            nextPages.add(p)
        }
        resetQueues((prevPages + cachedPage + nextPages).mapTo(HashSet()) { pageKey(it) })

        // Downloads in reading order, as the loader serves requests of equal priority.
        startDownload(cachedPage)
        nextPages.forEach { startDownload(it) }
        prevPages.forEach { startDownload(it) }

        nextPages.asReversed().forEach { preloadPage(it) }

        cachedPage.next?.let { preloadPage(it, prioritize = true) }
        preloadPage(cachedPage, prioritize = true)
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        // Empty too: lastIndex would be -1, and the requested page is read from it.
        val pages = chapters.currChapter.pages
        if (pages.isNullOrEmpty()) return

        this.viewerChapters = chapters

        // Re-setting chapters must not move the page - unless it is in another chapter, a switch made elsewhere (the
        // chapter keys, the menu), which the page left showing would undo by reporting itself selected.
        val shown = currentPage?.takeIf {
            when (it) {
                is ViewerReaderPage -> it.page.chapter == chapters.currChapter
                is ViewerTransitionPage ->
                    it.prevChapter == chapters.currChapter ||
                        it.nextChapter == chapters.currChapter
                else -> false
            }
        }
        val page = shown ?: getPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
        val anchor = getSpreadAnchor(page)
        currentPage = anchor
        progressPage(anchor)?.let { activity.onPageSelected(it.page) }
        preloadPages(anchor)

        viewerState.apply {
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
                    landscapeZoom(settled, delta > 0)
                    unzoomFarPages(settled)
                    if (!isContinuous) {
                        if (!activity.isScrollingThroughPages) {
                            activity.hideMenu()
                        }
                        progressPage(settled)?.let { activity.onPageSelected(it.page) }
                    }
                    preloadPages(settled)

                    if (!isContinuous) {
                        (settled as? ViewerTransitionPage)?.let { transitionPage ->
                            // Only past the last chapter, as the pager did
                            if (transitionPage.nextChapter == null) {
                                activity.showMenu()
                            }
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
        moveToPage(getSpreadAnchor(getPage(page)))
    }

    private fun moveToPage(newPage: ViewerPage) {
        val previousPage = currentPage
        // Before preloadPages below trims the cache - see [pinnedFromPage].
        val fromSpread = previousPage?.let { buildSpreadPage(it) }
        pinnedFromPage = fromSpread
        synchronized(lock) { flushDeferredCleanup() }

        currentPage = newPage
        progressPage(newPage)?.let { activity.onPageSelected(it.page) }
        preloadPages(newPage)

        (newPage as? ViewerTransitionPage)?.let { transitionPage ->
            // Only past the last chapter, as the pager did
            if (transitionPage.nextChapter == null) {
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

        landscapeZoom(newPage, direction >= 0)
        unzoomFarPages(newPage)
        if (direction != 0 && fromSpread != null) {
            animateTurn(direction, fromSpread)
        } else {
            viewerState.invalidate()
        }
    }

    /** How a [moveToPage] turn is shown. [direction] is 1 forward through the pages, -1 back. */
    protected open fun animateTurn(direction: Int, fromSpread: ImagePage) {
        viewerState.transitionFromPage = fromSpread
        viewerState.animatePageTurn(if (isReversed) direction else -direction)
    }

    // The other readers' key pan: a screen in 250ms, easing out.
    private val keyPanSpec = tween<Float>(250, easing = EaseOutQuad)

    override fun moveForward() = if (isReversed) moveLeft() else moveRight()

    override fun moveBackward() = if (isReversed) moveRight() else moveLeft()

    protected open fun moveRight() {
        viewerState.getPage(0)?.let { page ->
            if (paged.navigateToPan) {
                val minX = page.minX(page.scale)
                val maxX = page.maxX(page.scale)
                // Where it sits, mid pan or not: a press during a pan pans on from there rather than turning the
                // page, as the other readers' did.
                val currentX = page.x

                val c = if (isVertical && imageZoomType == ZoomStartPosition.RIGHT) -1 else 1
                val x = (currentX - c / page.scale).coerceIn(minX, maxX)

                if (!currentX.closeTo(x)) {
                    page.animateTo(targetX = x, targetY = page.y, animationSpec = keyPanSpec)
                    return
                }
            }

            navigateSpread(if (isReversed) -1 else 1)
        }
    }

    protected open fun moveLeft() {
        viewerState.getPage(0)?.let { page ->
            if (paged.navigateToPan) {
                val minX = page.minX(page.scale)
                val maxX = page.maxX(page.scale)
                val currentX = page.x

                val c = if (isVertical && imageZoomType == ZoomStartPosition.RIGHT) -1 else 1
                val x = (currentX + c / page.scale).coerceIn(minX, maxX)

                if (!currentX.closeTo(x)) {
                    page.animateTo(targetX = x, targetY = page.y, animationSpec = keyPanSpec)
                    return
                }
            }

            navigateSpread(if (isReversed) 1 else -1)
        }
    }

    /** Target anchor page one spread past [from], in [direction] (positive = forward). */
    private fun nextPage(from: ViewerPage, direction: Int): ViewerPage? {
        var page = getSpreadAnchor(from)

        page = if (direction > 0) {
            if (page is ViewerReaderPage && spreadPartner(page) != null) {
                page.next?.next ?: return null
            } else {
                page.next ?: return null
            }
        } else {
            page.prev ?: return null
        }

        return getSpreadAnchor(page)
    }

    private fun navigateSpread(direction: Int) {
        val target = currentPage?.let { nextPage(it, direction) } ?: return
        moveToPage(target)
    }
}
