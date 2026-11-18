package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionView
import eu.kanade.tachiyomi.ui.reader.viewer.transitionDownloadState
import eu.kanade.tachiyomi.util.system.dpToPx

/**
 * Holder of the webtoon viewer that contains a chapter transition.
 */
class WebtoonTransitionHolder(
    val layout: LinearLayout,
    viewer: WebtoonViewer,
) : WebtoonBaseHolder(layout, viewer) {

    private val transitionView = ReaderTransitionView(context)

    init {
        layout.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER

        val paddingVertical = 128.dpToPx
        val paddingHorizontal = 32.dpToPx
        layout.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        layout.addView(transitionView)
    }

    /**
     * Binds the given [transition] with this view holder.
     */
    fun bind(transition: ChapterTransition) {
        transitionView.bind(
            transition = transition,
            downloadState = viewer.downloadManager.transitionDownloadState(
                transition,
                viewer.activity.viewModel.manga,
                viewer.activity.viewModel.source,
            ),
            onRetry = viewer.activity::requestPreloadChapter,
            statusPadding = PaddingValues(vertical = 16.dp),
        )
    }
}
