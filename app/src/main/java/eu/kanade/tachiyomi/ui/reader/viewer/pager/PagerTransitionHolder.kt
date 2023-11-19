package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.progressindicator.CircularProgressIndicator
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderButton
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionView
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * View of the ViewPager that contains a chapter transition.
 */
@SuppressLint("ViewConstructor")
class PagerTransitionHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val transition: ChapterTransition,
) : LinearLayout(readerThemedContext), ViewPagerAdapter.PositionableView {

    private val scope = MainScope()
    private var stateJob: Job? = null

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item: Any
        get() = transition

    /**
     * View container of the current status of the transition page. Child views will be added
     * dynamically.
     */
    private var pagesContainer = LinearLayout(context).apply {
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        orientation = VERTICAL
        gravity = Gravity.CENTER
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val sidePadding = 64.dpToPx
        setPadding(sidePadding, 0, sidePadding, 0)

        val transitionView = ReaderTransitionView(context)
        addView(transitionView)
        addView(pagesContainer)

        transitionView.bind(transition, viewer.downloadManager, viewer.activity.viewModel.manga)

        transition.to?.let(::observeStatus)
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stateJob?.cancel()
    }

    /**
     * Observes the status of the page list of the next/previous chapter. Whenever there's a new
     * state, the pages container is cleaned up before setting the new state.
     */
    private fun observeStatus(chapter: ReaderChapter) {
        stateJob?.cancel()
        stateJob = scope.launch {
            chapter.stateFlow
                .collectLatest { state ->
                    pagesContainer.removeAllViews()
                    when (state) {
                        is ReaderChapter.State.Loading -> setLoading()
                        is ReaderChapter.State.Error -> setError(state.error)
                        is ReaderChapter.State.Wait, is ReaderChapter.State.Loaded -> {
                            // No additional view is added
                        }
                    }
                }
        }
    }

    /**
     * Sets the loading state on the pages container.
     */
    private fun setLoading() {
        val progress = CircularProgressIndicator(context)
        progress.isIndeterminate = true

        val textView = AppCompatTextView(context).apply {
            wrapContent()
            text = context.stringResource(MR.strings.transition_pages_loading)
        }

        pagesContainer.addView(progress)
        pagesContainer.addView(textView)
    }

    /**
     * Sets the error state on the pages container.
     */
    private fun setError(error: Throwable) {
        val textView = AppCompatTextView(context).apply {
            wrapContent()
            text = context.stringResource(MR.strings.transition_pages_error, error.message ?: "")
        }

        val retryBtn = ReaderButton(context).apply {
            viewer = this@PagerTransitionHolder.viewer
            wrapContent()
            text = context.stringResource(MR.strings.action_retry)
            setOnClickListener {
                val toChapter = transition.to
                if (toChapter != null) {
                    this@PagerTransitionHolder.viewer.activity.requestPreloadChapter(toChapter)
                }
            }
        }

        pagesContainer.addView(textView)
        pagesContainer.addView(retryBtn)
    }

    /**
     * Extension method to set layout params to wrap content on this view.
     */
    private fun View.wrapContent() {
        layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    }
}
