package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.view.LayoutInflater
import androidx.core.view.isVisible
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
    private val spread: PageSpread? = null,
    // Non-null when this is a *lone* page to justify to its binding side (spread mode, SoloPage =
    // JUSTIFY): true → left half, false → right half. Null keeps the default centred full-screen fit.
    private val soloJustifyIsLeft: Boolean? = null,
) : ReaderPageImageView(readerThemedContext), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page

    /**
     * Whether spread mode is currently active on a spread-capable pager. When true the spread owns
     * wide-page presentation (option A): the single-page wide-image reformatters (split,
     * rotate-to-fit and landscape-zoom) stay inert, so a wide page is shown full-width as the
     * spread it already is, whether it's still inside a [PageSpread] or has been broken out into a
     * solo holder ([spread] `== null` but spread mode still on). Mirrors the gate
     * `PagerViewerAdapter.pairPages` uses to decide pairing.
     */
    private val spreadModeActive: Boolean
        get() = (viewer is L2RPagerViewer || viewer is R2LPagerViewer) && viewer.config.spread

    /**
     * True once a decoded image is on screen. A shift/reload re-pairs the same pages, so a loaded
     * spread half can be detached and reused for its new pairing instead of being torn down and
     * re-decoded (see the holder pool in [PagerViewerAdapter]).
     */
    var hasLoadedImage = false
        private set

    /**
     * The crop-borders setting this half's bitmap was decoded with. A reused pooled half keeps its
     * bitmap rather than re-decoding, so the adapter checks this still matches the live config before
     * reusing it (the pool's reuse invariant; see [PagerViewerAdapter]).
     */
    var decodedCropBorders = false
        private set

    /**
     * Loading progress bar to indicate the current progress.
     */
    private var progressIndicator: ReaderProgressIndicator? = null // = ReaderProgressIndicator(readerThemedContext)

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    private val scope = MainScope()

    /**
     * Job for loading the page and processing changes to the page's status.
     */
    private var loadJob: Job? = null

    init {
        loadJob = scope.launch { loadPageAndProcessStatus() }
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(context)
            addView(progressIndicator)
        }
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val loader = page.chapter.pageLoader ?: return

        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue -> setQueued()
                    Page.State.LoadPage -> setLoading()
                    Page.State.DownloadImage -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator?.setProgress(value)
                        }
                    }
                    Page.State.Ready -> setImage()
                    is Page.State.Error -> setError(state.error)
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator?.setProgress(0)

        val streamFn = page.stream ?: return

        try {
            val (source, isAnimated, background) = withIOContext {
                val source = streamFn().use { process(item, Buffer().readFrom(it)) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                val background = if (!isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, source.peek().inputStream())
                } else {
                    null
                }
                Triple(source, isAnimated, background)
            }
            withUIContext {
                // A coordinated spread half, or a justified lone page, must not paint at its solo
                // fit before its final scale is applied, so defer the reveal (the coordinator, or
                // applySoloJustification, reveals it once placed). Ordinary solo pages are unaffected.
                if (spread != null || soloJustifyIsLeft != null) deferInitialReveal()
                decodedCropBorders = viewer.config.imageCropBorders
                setImage(
                    source,
                    isAnimated,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        // For a spread half, this is only the starting point: once both halves
                        // report their native size, SpreadScaleCoordinator overrides it with a
                        // scale computed jointly against the pair's combined shape. Applying the
                        // real preference here first (rather than a hardcoded fill-width) means a
                        // slow or failed sibling still leaves this half in a reasonable, correctly
                        // scale-typed state rather than an artificial one.
                        minimumScaleType = viewer.config.imageScaleType,
                        cropBorders = viewer.config.imageCropBorders,
                        zoomStartPosition = viewer.config.imageZoomType,
                        // Option A: in spread mode the spread owns wide-page presentation, so the
                        // single-page "auto-zoom into wide images" stays inert on spread pages (it
                        // would otherwise fire a delayed zoom over the coordinator's joint scale on
                        // a force-paired wide half). Resumes normally in single-page reading.
                        landscapeZoom = !spreadModeActive && viewer.config.landscapeZoom,
                    ),
                )
                if (!isAnimated) {
                    pageBackground = background
                }
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    private fun process(page: ReaderPage, imageSource: BufferedSource): BufferedSource {
        // Option A: while spread mode is active the spread owns wide-page presentation; a wide page
        // is shown full-width as the spread it already is (broken out of its pair if needed) and the
        // single-page wide-image reformatters below (rotate-to-fit, split) stay inert. This is keyed
        // off spread mode being active, not off this holder still being inside a PageSpread, so it
        // also covers a page that has just been broken out into a solo holder.
        if (spreadModeActive) {
            // Only a holder still inside the PageSpread can break the pair; force-pairing keeps a
            // wide page in the pair instead.
            if (spread != null && !viewer.config.spreadForcePairing && ImageUtil.isWideImage(imageSource)) {
                spread.let { viewer.onSpreadPageWide(it) }
            }
            return imageSource
        }

        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        if (!viewer.config.dualPageSplit) {
            return imageSource
        }

        if (page is InsertPage) {
            return splitInHalf(imageSource)
        }

        if (!ImageUtil.isWideImage(imageSource)) {
            return imageSource
        }

        onPageSplit(page)

        return splitInHalf(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): BufferedSource {
        return if (ImageUtil.isWideImage(imageSource)) {
            val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
            ImageUtil.rotateImage(imageSource, rotation)
        } else {
            imageSource
        }
    }

    private fun splitInHalf(imageSource: BufferedSource): BufferedSource {
        var side = when {
            viewer is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.RIGHT
            viewer !is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.LEFT
            viewer is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.LEFT
            viewer !is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.RIGHT
            else -> error("We should choose a side!")
        }

        if (viewer.config.dualPageInvert) {
            side = when (side) {
                ImageUtil.Side.RIGHT -> ImageUtil.Side.LEFT
                ImageUtil.Side.LEFT -> ImageUtil.Side.RIGHT
            }
        }

        return ImageUtil.splitInHalf(imageSource, side)
    }

    private fun onPageSplit(page: ReaderPage) {
        val newPage = InsertPage(page)
        viewer.onPageSplit(page, newPage)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError(error: Throwable?) {
        progressIndicator?.hide()
        showErrorLayout(error)
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        hasLoadedImage = true
        progressIndicator?.hide()
        applySoloJustification()
        // A justified lone page has now been placed (or is exempt); reveal it. Spread halves are
        // instead revealed together by SpreadScaleCoordinator once the joint scale is applied.
        if (spread == null && soloJustifyIsLeft != null) revealDeferredImage()
    }

    /**
     * Justifies a lone page to its binding side once its native size is known: fit it into one
     * half-width slot against the seam, other half blank, the recto/verso of a book. Skipped for
     * wide pages (they span both page-widths and are shown full-width), or when this isn't a
     * justified lone page. Geometry is the pure [SpreadScaleCalculator.soloPlacement].
     */
    private fun applySoloJustification() {
        val alignLeft = soloJustifyIsLeft ?: return
        val nativeWidth = sWidth
        val nativeHeight = sHeight
        if (nativeWidth <= 0 || nativeHeight <= 0 || width <= 0 || height <= 0) return
        // A wide page spans both page-widths; leave it full-screen rather than confining it to a half.
        if (nativeWidth > nativeHeight) return
        val placement = SpreadScaleCalculator.soloPlacement(
            nativeWidth = nativeWidth,
            nativeHeight = nativeHeight,
            viewportWidth = width,
            viewportHeight = height,
            scaleType = SpreadScaleCoordinator.scaleTypeOf(viewer.config.imageScaleType),
            alignLeft = alignLeft,
        )
        setSpreadScaleAndCenter(placement.scale, PointF(placement.centerX, placement.centerY))
        setSpreadPadding(
            left = placement.padding.left,
            top = placement.padding.top,
            right = placement.padding.right,
            bottom = placement.padding.bottom,
        )
    }

    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError(error: Throwable?) {
        super.onImageLoadError(error)
        setError(error)
        // Never leave a deferred half stuck INVISIBLE behind its error UI. For a spread, the sibling
        // is revealed by the coordinator (wired to onImageLoadError → onHolderError).
        revealDeferredImage()
    }

    /**
     * Called when an image is zoomed in/out.
     */
    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.activity.hideMenu()
    }

    private fun showErrorLayout(error: Throwable?): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }

        val imageUrl = page.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.viewer = viewer
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val sourceId = viewer.activity.viewModel.manga?.source

                    val intent = WebViewActivity.newIntent(context, imageUrl, sourceId)
                    context.startActivity(intent)
                }
            }
        }

        errorLayout?.errorMessage?.text = with(context) { error?.formattedMessage }
            ?: context.stringResource(MR.strings.decode_image_error)

        errorLayout?.root?.isVisible = true
        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.root?.isVisible = false
        errorLayout = null
    }
}
