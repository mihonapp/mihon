package eu.kanade.tachiyomi.ui.manga.chapter

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import eu.kanade.presentation.components.ChapterDownloadIndicator
import eu.kanade.presentation.manga.ChapterDownloadAction
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.data.download.model.Download

class ChapterDownloadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AbstractComposeView(context, attrs, defStyle) {

    private var state by mutableStateOf(Download.State.NOT_DOWNLOADED)
    private var progress by mutableStateOf(0)

    var listener: (ChapterDownloadAction) -> Unit = {}

    @Composable
    override fun Content() {
        TachiyomiTheme {
            ChapterDownloadIndicator(
                downloadStateProvider = { state },
                downloadProgressProvider = { progress },
                onClick = listener,
            )
        }
    }

    fun setState(state: Download.State, progress: Int = 0) {
        this.state = state
        this.progress = progress
    }
}
