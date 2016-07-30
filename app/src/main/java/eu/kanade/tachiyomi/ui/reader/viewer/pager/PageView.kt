package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.base.PageDecodeErrorLayout
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader.Companion.ALIGN_CENTER
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader.Companion.ALIGN_LEFT
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader.Companion.ALIGN_RIGHT
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.RightToLeftReader
import eu.kanade.tachiyomi.ui.reader.viewer.pager.vertical.VerticalReader
import kotlinx.android.synthetic.main.chapter_image.view.*
import kotlinx.android.synthetic.main.item_pager_reader.view.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject
import java.io.File
import java.util.concurrent.TimeUnit

class PageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
: FrameLayout(context, attrs) {

    /**
     * Page of a chapter.
     */
    var page: Page? = null
        private set

    /**
     * Subscription for progress changes of the page.
     */
    private var progressSubscription: Subscription? = null

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    fun initialize(reader: PagerReader, page: Page?) {
        val activity = reader.activity as ReaderActivity

        when (activity.readerTheme) {
            ReaderActivity.BLACK_THEME -> progress_text.setTextColor(reader.whiteColor)
            ReaderActivity.WHITE_THEME -> progress_text.setTextColor(reader.blackColor)
        }

        if (reader is RightToLeftReader) {
            rotation = -180f
        }

        with(image_view) {
            setMaxBitmapDimensions((reader.activity as ReaderActivity).maxBitmapSize)
            setDoubleTapZoomStyle(com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.ZOOM_FOCUS_FIXED)
            setPanLimit(com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumScaleType(reader.scaleType)
            setMinimumDpi(50)
            setRegionDecoderClass(reader.regionDecoderClass)
            setBitmapDecoderClass(reader.bitmapDecoderClass)
            setVerticalScrollingParent(reader is VerticalReader)
            setOnTouchListener { v, motionEvent -> reader.gestureDetector.onTouchEvent(motionEvent) }
            setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    when (reader.zoomType) {
                        ALIGN_LEFT -> setScaleAndCenter(scale, android.graphics.PointF(0f, 0f))
                        ALIGN_RIGHT -> setScaleAndCenter(scale, android.graphics.PointF(sWidth.toFloat(), 0f))
                        ALIGN_CENTER -> {
                            val newCenter = center
                            newCenter.y = 0f
                            setScaleAndCenter(scale, newCenter)
                        }
                    }
                }

                override fun onImageLoadError(e: Exception) {
                    onImageDecodeError(activity)
                }
            })
        }

        retry_button.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                activity.presenter.retryPage(page)
            }
            true
        }

        if (page != null) {
            this.page = page
            observeStatus()
        }
    }

    fun cleanup() {
        unsubscribeProgress()
        unsubscribeStatus()
        image_view.setOnTouchListener(null)
        image_view.setOnImageEventListener(null)
    }

    override fun onDetachedFromWindow() {
        cleanup()
        super.onDetachedFromWindow()
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        statusSubscription?.unsubscribe()
        val page = page ?: return

        val statusSubject = SerializedSubject(PublishSubject.create<Int>())
        page.setStatusSubject(statusSubject)

        statusSubscription = statusSubject.startWith(page.status)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { processStatus(it) }
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        progressSubscription?.unsubscribe()
        val page = page ?: return

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
                .map { page.progress }
                .distinctUntilChanged()
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { progress ->
                    progress_text.text = context.getString(R.string.download_progress, progress)
                }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Int) {
        when (status) {
            Page.QUEUE -> hideError()
            Page.LOAD_PAGE -> onLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress()
                onDownloading()
            }
            Page.READY -> {
                onReady()
                unsubscribeProgress()
            }
            Page.ERROR -> {
                onError()
                unsubscribeProgress()
            }
        }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus() {
        page?.setStatusSubject(null)
        statusSubscription?.unsubscribe()
        statusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress() {
        progressSubscription?.unsubscribe()
        progressSubscription = null
    }

    /**
     * Called when the page is loading.
     */
    private fun onLoading() {
        progress_container.visibility = View.VISIBLE
        progress_text.visibility = View.VISIBLE
        progress_text.setText(R.string.downloading)
    }

    /**
     * Called when the page is downloading.
     */
    private fun onDownloading() {
        progress_container.visibility = View.VISIBLE
        progress_text.visibility = View.VISIBLE
    }

    /**
     * Called when the page is ready.
     */
    private fun onReady() {
        page?.imagePath?.let { path ->
            if (File(path).exists()) {
                image_view.setImage(ImageSource.uri(path))
                progress_container.visibility = View.GONE
            } else {
                page?.status = Page.ERROR
            }
        }
    }

    /**
     * Called when the page has an error.
     */
    private fun onError() {
        progress_container.visibility = View.GONE
        retry_button.visibility = View.VISIBLE
    }

    /**
     * Hides the error layout.
     */
    private fun hideError() {
        retry_button.visibility = View.GONE
    }

    /**
     * Called when an image fails to decode.
     */
    private fun onImageDecodeError(activity: ReaderActivity) {
        page?.let { page ->
            val errorLayout = PageDecodeErrorLayout(context, page, activity.readerTheme,
                    { activity.presenter.retryPage(page) })

            addView(errorLayout)
        }
    }

}