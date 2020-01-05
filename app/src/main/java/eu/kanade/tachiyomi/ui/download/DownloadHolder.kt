package eu.kanade.tachiyomi.ui.download

import android.view.View
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.download_item.*

/**
 * Class used to hold the data of a download.
 * All the elements from the layout file "download_item" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @constructor creates a new download holder.
 */
class DownloadHolder(view: View, val adapter: DownloadAdapter) : BaseFlexibleViewHolder(view, adapter) {

    init {
        setDragHandleView(reorder)
    }

    private lateinit var download: Download

    /**
     * Binds this holder with the given category
     *
     * @param category The category to bind
     */
    fun bind(download: Download) {
        this.download = download

        // Update the chapter name.
        chapter_title.text = download.chapter.name

        // Update the manga title
        manga_title.text = download.manga.title

        // Update the progress bar and the number of downloaded pages
        val pages = download.pages
        if (pages == null) {
            download_progress.progress = 0
            download_progress.max = 1
            download_progress_text.text = ""
        } else {
            download_progress.max = pages.size * 100
            notifyProgress()
            notifyDownloadedPages()
        }
    }

    /**
     * Updates the progress bar of the download.
     */
    fun notifyProgress() {
        val pages = download.pages ?: return
        if (download_progress.max == 1) {
            download_progress.max = pages.size * 100
        }
        download_progress.progress = download.totalProgress
    }

    /**
     * Updates the text field of the number of downloaded pages.
     */
    fun notifyDownloadedPages() {
        val pages = download.pages ?: return
        download_progress_text.text = "${download.downloadedImages}/${pages.size}"
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        adapter.onItemReleaseListener.onItemReleased(position)
    }

}
