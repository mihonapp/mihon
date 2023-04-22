package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.util.view.setComposeContent
import tachiyomi.domain.manga.model.Manga

class ReaderTransitionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    init {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    fun bind(transition: ChapterTransition, downloadManager: DownloadManager, manga: Manga?) {
        manga ?: return

        removeAllViews()

        val transitionView = ComposeView(context).apply {
            setComposeContent {
                ChapterTransition(
                    transition = transition,
                    downloadManager = downloadManager,
                    manga = manga,
                )
            }
        }
        addView(transitionView)
    }
}
