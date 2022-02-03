package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.res.Resources
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Holder of the webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    viewer: WebtoonViewer
) : WebtoonBaseHolder(frame, viewer) {

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressIndicator = createProgressIndicator()

    /**
     * Progress bar container. Needed to keep a minimum height size of the holder, otherwise the
     * adapter would create more views to fill the screen, which is not wanted.
     */
    private lateinit var progressContainer: ViewGroup

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    /**
     * Getter to retrieve the height of the recycler view.
     */
    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var progressSubscription: Subscription? = null

    /**
     * Subscription used to read the header of the image. This is needed in order to instantiate
     * the appropiate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderSubscription: Subscription? = null

    init {
        refreshLayoutParams()

        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { onImageDecodeError() }
        frame.onScaleChanged = { viewer.activity.hideMenu() }
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        observeStatus()
        refreshLayoutParams()
    }

    private fun refreshLayoutParams() {
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            if (!viewer.isContinuous) {
                bottomMargin = 15.dpToPx
            }

            val margin = Resources.getSystem().displayMetrics.widthPixels * (viewer.config.sidePadding / 100f)
            marginEnd = margin.toInt()
            marginStart = margin.toInt()
        }
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    override fun recycle() {
        unsubscribeStatus()
        unsubscribeProgress()
        unsubscribeReadImageHeader()

        removeErrorLayout()
        frame.recycle()
        progressIndicator.setProgress(0, animated = false)
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        unsubscribeStatus()

        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { processStatus(it) }

        addSubscription(statusSubscription)
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        unsubscribeProgress()

        val page = page ?: return

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { page.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value -> progressIndicator.setProgress(value) }

        addSubscription(progressSubscription)
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Int) {
        when (status) {
            Page.QUEUE -> setQueued()
            Page.LOAD_PAGE -> setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress()
                setDownloading()
            }
            Page.READY -> {
                setImage()
                unsubscribeProgress()
            }
            Page.ERROR -> {
                setError()
                unsubscribeProgress()
            }
        }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus() {
        removeSubscription(statusSubscription)
        statusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress() {
        removeSubscription(progressSubscription)
        progressSubscription = null
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun unsubscribeReadImageHeader() {
        removeSubscription(readImageHeaderSubscription)
        readImageHeaderSubscription = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressIndicator.setProgress(0)
        removeErrorLayout()

        unsubscribeReadImageHeader()
        val streamFn = page?.stream ?: return

        var openStream: InputStream? = null
        readImageHeaderSubscription = Observable
            .fromCallable {
                val stream = streamFn().buffered(16)
                openStream = process(stream)

                ImageUtil.isAnimatedAndSupported(stream)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { isAnimated ->
                frame.setImage(
                    openStream!!,
                    isAnimated,
                    ReaderPageImageView.Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                        cropBorders = viewer.config.imageCropBorders
                    )
                )
            }
            // Keep the Rx stream alive to close the input stream only when unsubscribed
            .flatMap { Observable.never<Unit>() }
            .doOnUnsubscribe { openStream?.close() }
            .subscribe({}, {})

        addSubscription(readImageHeaderSubscription)
    }

    private fun process(imageStream: InputStream): InputStream {
        if (!viewer.config.dualPageSplit) {
            return imageStream
        }

        val isDoublePage = ImageUtil.isDoublePage(imageStream)
        if (!isDoublePage) {
            return imageStream
        }

        val upperSide = if (viewer.config.dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
        return ImageUtil.splitAndMerge(imageStream, upperSide)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressContainer.isVisible = false
        initErrorLayout(withOpenInWebView = false)
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.isVisible = false
    }

    /**
     * Called when the image fails to decode.
     */
    private fun onImageDecodeError() {
        progressContainer.isVisible = false
        initErrorLayout(withOpenInWebView = true)
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressIndicator(): ReaderProgressIndicator {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressIndicator(context).apply {
            updateLayoutParams<FrameLayout.LayoutParams> {
                gravity = Gravity.CENTER_HORIZONTAL
                updateMargins(top = parentHeight / 4)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initErrorLayout(withOpenInWebView: Boolean): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), frame, true)
            errorLayout?.root?.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, (parentHeight * 0.8).toInt())
            errorLayout?.actionRetry?.setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }
            val imageUrl = page?.imageUrl
            if (imageUrl.orEmpty().startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val intent = WebViewActivity.newIntent(context, imageUrl!!)
                    context.startActivity(intent)
                }
            }
        }
        errorLayout?.actionOpenInWebView?.isVisible = withOpenInWebView
        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it.root)
            errorLayout = null
        }
    }
}
