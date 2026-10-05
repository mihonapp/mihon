package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.CropBorders
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import tachiyomi.core.common.util.system.panel.ContourPanelDetector
import tachiyomi.core.common.util.system.panel.PanelOrder
import tachiyomi.core.common.util.system.panel.PanelRect
import tachiyomi.i18n.MR

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
) : ReaderPageImageView(readerThemedContext), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page

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

    /**
     * Detected panels of the displayed image in reading order, normalised to 0..1 of the image as
     * shown (after split and crop). Empty when panel navigation is off, detection hasn't finished,
     * failed, or found fewer than 2 panels.
     */
    private var panels: List<PanelRect> = emptyList()

    /** Whether panel navigation has at least two panels to step through on this page. */
    val hasPanels: Boolean
        get() = panels.size >= 2

    /**
     * Index into [panels] of the panel currently zoomed to, or -1 for the full page.
     */
    private var panelIndex = -1

    /**
     * Job running panel detection for the current image, off the main thread.
     */
    private var detectJob: Job? = null

    /**
     * Set by [process] when [rotateDualPage] actually rotated the image. Rotated pages skip
     * panel detection.
     */
    private var rotated = false

    /**
     * True once detection has completed for the current image (with or without panels found).
     */
    private var detectionFinished = false

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
        detectJob?.cancel()
        detectJob = null
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
        detectJob?.cancel()
        detectJob = null
        panels = emptyList()
        panelIndex = -1
        detectionFinished = false
        updatePanelMask()

        val streamFn = page.stream ?: return

        try {
            val (source, isAnimated, background, analysisSource) = withIOContext {
                val source = streamFn().use { process(item, Buffer().readFrom(it)) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                val background = if (!isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, source.peek().inputStream())
                } else {
                    null
                }
                // Independent in-memory copy for panel detection; decoding happens later off the IO path.
                val analysisSource = if (viewer.config.panelNavigation && !isAnimated && !rotated) {
                    Buffer().also { source.peek().readAll(it) }
                } else {
                    null
                }
                LoadedImage(source, isAnimated, background, analysisSource)
            }
            withUIContext {
                setImage(
                    source,
                    isAnimated,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = viewer.config.imageScaleType,
                        cropBorders = viewer.config.imageCropBorders,
                        zoomStartPosition = viewer.config.imageZoomType,
                        landscapeZoom = viewer.config.landscapeZoom && !viewer.config.panelNavigation,
                    ),
                )
                if (!isAnimated) {
                    pageBackground = background
                }
                removeErrorLayout()
            }
            if (analysisSource != null) {
                detectJob = scope.launch(Dispatchers.Default) { detectPanels(analysisSource) }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    /**
     * Runs panel detection on [analysisSource] (the processed image) and publishes the result to
     * [panels]. Falls back to landscape zoom when fewer than 2 panels are found.
     */
    private suspend fun detectPanels(analysisSource: BufferedSource) {
        try {
            val gray = ImageUtil.decodeGrayForAnalysis(
                analysisSource,
                findCrop = if (viewer.config.imageCropBorders) {
                    { px, w, h -> CropBorders.findCropBorders(px, w, h) }
                } else {
                    null
                },
            )
            val start = System.currentTimeMillis()
            val ordered = if (gray != null) {
                val found = ContourPanelDetector().detect(gray)
                PanelOrder.sort(found, rightToLeft = viewer is R2LPagerViewer, isSpread = gray.width > gray.height)
            } else {
                emptyList()
            }
            logcat {
                "Panel detection: page ${page.number}, image ${gray?.width}x${gray?.height}, " +
                    "${ordered.size} panels in ${System.currentTimeMillis() - start}ms"
            }
            withUIContext {
                panels = if (ordered.size >= 2) ordered else emptyList()
                detectionFinished = true
                if (panels.isEmpty() && viewer.config.landscapeZoom && isVisibleOnScreen()) {
                    applyLandscapeZoom()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // panels stays empty (reset in setImage), so navigation falls back to page turns.
            logcat(LogPriority.ERROR, e) { "Panel detection failed" }
        }
    }

    override fun onPageSelected(forward: Boolean) {
        super.onPageSelected(forward)
        if (viewer.config.panelNavigation && viewer.config.landscapeZoom && detectionFinished && panels.isEmpty()) {
            applyLandscapeZoom(forward)
        }
    }

    /**
     * Panel cursor API: [panelIndex] -1 means the full page. Each function returns true only if it
     * consumed the navigation and the zoom was applied; the index never moves unless the zoom
     * succeeded, so callers fall through to a normal page turn on false.
     */
    fun stepPanelForward(): Boolean {
        if (panels.size < 2 || panelIndex == panels.lastIndex) return false
        if (!zoomToRegion(panels[panelIndex + 1])) return false
        panelIndex++
        updatePanelMask()
        return true
    }

    fun stepPanelBackward(): Boolean {
        if (panelIndex > 0) {
            if (!zoomToRegion(panels[panelIndex - 1])) return false
            panelIndex--
            updatePanelMask()
            return true
        }
        if (panelIndex == 0) {
            if (!zoomToFullPage()) return false
            panelIndex = -1
            updatePanelMask()
            return true
        }
        return false
    }

    fun enterAtLastPanel(): Boolean {
        if (panels.size < 2 || !zoomToRegion(panels.last(), animate = false)) return false
        panelIndex = panels.lastIndex
        updatePanelMask()
        return true
    }

    fun resetPanelCursor() {
        if (panelIndex != -1) {
            panelIndex = -1
            zoomToFullPage(animate = false)
            updatePanelMask()
        }
    }

    /** Masks everything outside the current panel when panel isolation is on; clears it otherwise. */
    private fun updatePanelMask() {
        val region = if (viewer.config.panelIsolation && panelIndex >= 0) panels.getOrNull(panelIndex) else null
        setPanelMask(region, context.readerBackgroundColor(viewer.config.theme))
    }

    private fun process(page: ReaderPage, imageSource: BufferedSource): BufferedSource {
        rotated = false
        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        if (!viewer.config.dualPageSplit) {
            return imageSource
        }

        if (page is InsertPage) {
            return splitInHalf(imageSource)
        }

        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return imageSource
        }

        onPageSplit(page)

        return splitInHalf(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): BufferedSource {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        return if (isDoublePage) {
            val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
            rotated = true
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

    private data class LoadedImage(
        val source: BufferedSource,
        val isAnimated: Boolean,
        val background: Drawable?,
        val analysisSource: Buffer?,
    )

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
        progressIndicator?.hide()
    }

    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError(error: Throwable?) {
        super.onImageLoadError(error)
        setError(error)
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
