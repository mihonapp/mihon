package eu.kanade.tachiyomi.ui.manga.chapter.base

import android.view.View
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.presentation.manga.ChapterDownloadAction

open class BaseChapterHolder(
    view: View,
    private val adapter: BaseChaptersAdapter<*>,
) : FlexibleViewHolder(view, adapter) {

    val downloadActionListener: (ChapterDownloadAction) -> Unit = { action ->
        when (action) {
            ChapterDownloadAction.START -> adapter.clickListener.downloadChapter(bindingAdapterPosition)
            ChapterDownloadAction.START_NOW -> adapter.clickListener.startDownloadNow(bindingAdapterPosition)
            ChapterDownloadAction.CANCEL, ChapterDownloadAction.DELETE -> {
                adapter.clickListener.deleteChapter(bindingAdapterPosition)
            }
        }
    }
}
