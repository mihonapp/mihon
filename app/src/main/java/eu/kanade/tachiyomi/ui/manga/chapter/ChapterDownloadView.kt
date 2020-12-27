package eu.kanade.tachiyomi.ui.manga.chapter

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.ChapterDownloadViewBinding

class ChapterDownloadView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val binding: ChapterDownloadViewBinding

    init {
        binding = ChapterDownloadViewBinding.inflate(LayoutInflater.from(context), this, false)
        addView(binding.root)
    }

    fun setState(state: Download.State) {
        binding.downloadIconBorder.isVisible = state == Download.State.NOT_DOWNLOADED
        binding.downloadIcon.isVisible = state == Download.State.NOT_DOWNLOADED || state == Download.State.DOWNLOADING

        binding.downloadProgress.isVisible = state == Download.State.DOWNLOADING || state == Download.State.QUEUE
        // TODO: show actual download progress

        binding.downloadedIcon.isVisible = state == Download.State.DOWNLOADED

        binding.errorIcon.isVisible = state == Download.State.ERROR
    }

    // TODO: onClick actions
}
