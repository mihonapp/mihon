package eu.kanade.tachiyomi.ui.manga.chapter

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.databinding.ChapterDownloadViewBinding

class ChapterDownloadView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val binding: ChapterDownloadViewBinding

    init {
        binding = ChapterDownloadViewBinding.inflate(LayoutInflater.from(context), this, false)
        addView(binding.root)
    }

    fun setState(state: State) {
        binding.downloadIconBorder.isVisible = state == State.DOWNLOAD || state == State.ERROR
        binding.downloadIcon.isVisible = state == State.DOWNLOAD || state == State.DOWNLOADING

        binding.downloadProgress.isVisible = state == State.DOWNLOADING || state == State.QUEUED

        binding.downloadedIcon.isVisible = state == State.DOWNLOADED
    }

    enum class State {
        DOWNLOAD,
        QUEUED,
        DOWNLOADING,
        ERROR,
        DOWNLOADED,
    }
}
