package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream

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
    private val progressIndicator: ReaderProgressIndicator = ReaderProgressIndicator(readerThemedContext).apply {
        updateLayoutParams<LayoutParams> {
            gravity = Gravity.CENTER
        }
    }

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    private val scope = MainScope()

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Job for progress changes of the page.
     */
    private var progressJob: Job? = null

    /**
     * Subscription used to read the header of the image. This is needed in order to instantiate
     * the appropiate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderSubscription: Subscription? = null

    init {
        addView(progressIndicator)
        observeStatus()
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelProgressJob()
        unsubscribeStatus()
        unsubscribeReadImageHeader()
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        statusSubscription?.unsubscribe()

        val loader = page.chapter.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { processStatus(it) }
    }

    private fun launchProgressJob() {
        progressJob?.cancel()
        progressJob = scope.launch {
            page.progressFlow.collectLatest { value -> progressIndicator.setProgress(value) }
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Page.State) {
        when (status) {
            Page.State.QUEUE -> setQueued()
            Page.State.LOAD_PAGE -> setLoading()
            Page.State.DOWNLOAD_IMAGE -> {
                launchProgressJob()
                setDownloading()
            }
            Page.State.READY -> {
                setImage()
                cancelProgressJob()
            }
            Page.State.ERROR -> {
                setError()
                cancelProgressJob()
            }
        }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus() {
        statusSubscription?.unsubscribe()
        statusSubscription = null
    }

    private fun cancelProgressJob() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun unsubscribeReadImageHeader() {
        readImageHeaderSubscription?.unsubscribe()
        readImageHeaderSubscription = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressIndicator.show()
        errorLayout?.root?.isVisible = false
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressIndicator.show()
        errorLayout?.root?.isVisible = false
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        progressIndicator.show()
        errorLayout?.root?.isVisible = false
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressIndicator.setProgress(0)
        errorLayout?.root?.isVisible = false

        unsubscribeReadImageHeader()
        val streamFn = page.stream ?: return

        readImageHeaderSubscription = Observable
            .fromCallable {
                val stream = streamFn().buffered(16)
                val itemStream = process(item, stream)
                val bais = ByteArrayInputStream(itemStream.readBytes())
                try {
                    val isAnimated = ImageUtil.isAnimatedAndSupported(bais)
                    bais.reset()
                    val background = if (!isAnimated && viewer.config.automaticBackground) {
                        ImageUtil.chooseBackground(context, bais)
                    } else {
                        null
                    }
                    bais.reset()
                    Triple(bais, isAnimated, background)
                } finally {
                    stream.close()
                    itemStream.close()
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { (bais, isAnimated, background) ->
                bais.use {
                    setImage(
                        it,
                        isAnimated,
                        Config(
                            zoomDuration = viewer.config.doubleTapAnimDuration,
                            minimumScaleType = viewer.config.imageScaleType,
                            cropBorders = viewer.config.imageCropBorders,
                            zoomStartPosition = viewer.config.imageZoomType,
                            landscapeZoom = viewer.config.landscapeZoom,
                        ),
                    )
                    if (!isAnimated) {
                        pageBackground = background
                    }
                }
            }
            .subscribe({}, {})
    }

    private fun process(page: ReaderPage, imageStream: BufferedInputStream): InputStream {
        if (!viewer.config.dualPageSplit) {
            return imageStream
        }

        if (page is InsertPage) {
            return splitInHalf(imageStream)
        }

        val isDoublePage = ImageUtil.isWideImage(imageStream)
        if (!isDoublePage) {
            return imageStream
        }

        onPageSplit(page)

        return splitInHalf(imageStream)
    }

    private fun splitInHalf(imageStream: InputStream): InputStream {
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

        return ImageUtil.splitInHalf(imageStream, side)
    }

    private fun onPageSplit(page: ReaderPage) {
        val newPage = InsertPage(page)
        viewer.onPageSplit(page, newPage)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressIndicator.hide()
        showErrorLayout(withOpenInWebView = false)
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        progressIndicator.hide()
    }

    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError() {
        super.onImageLoadError()
        progressIndicator.hide()
        showErrorLayout(withOpenInWebView = true)
    }

    /**
     * Called when an image is zoomed in/out.
     */
    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.activity.hideMenu()
    }

    private fun showErrorLayout(withOpenInWebView: Boolean): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
            val imageUrl = page.imageUrl
            if (imageUrl.orEmpty().startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.viewer = viewer
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val intent = WebViewActivity.newIntent(context, imageUrl!!)
                    context.startActivity(intent)
                }
            }
        }
        errorLayout?.actionOpenInWebView?.isVisible = withOpenInWebView
        errorLayout?.root?.isVisible = true
        return errorLayout!!
    }
}
