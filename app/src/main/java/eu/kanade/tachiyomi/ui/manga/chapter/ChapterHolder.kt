package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import android.widget.PopupMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import java.util.Date
import kotlinx.android.synthetic.main.chapters_item.chapter_description
import kotlinx.android.synthetic.main.chapters_item.chapter_menu
import kotlinx.android.synthetic.main.chapters_item.chapter_title
import kotlinx.android.synthetic.main.chapters_item.download_text

class ChapterHolder(
    private val view: View,
    private val adapter: ChaptersAdapter
) : BaseFlexibleViewHolder(view, adapter) {

    init {
        // We need to post a Runnable to show the popup to make sure that the PopupMenu is
        // correctly positioned. The reason being that the view may change position before the
        // PopupMenu is shown.
        chapter_menu.setOnClickListener { it.post { showPopupMenu(it) } }
    }

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter

        chapter_title.text = when (manga.displayMode) {
            Manga.DISPLAY_NUMBER -> {
                val number = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
                itemView.context.getString(R.string.display_mode_chapter, number)
            }
            else -> chapter.name
        }

        // Set correct text color
        val chapterColor = if (chapter.read) adapter.readColor else adapter.unreadColor
        chapter_title.setTextColor(chapterColor)
        chapter_description.setTextColor(chapterColor)
        if (chapter.bookmark) {
            chapter_title.setTextColor(adapter.bookmarkedColor)
        }

        val descriptions = mutableListOf<String>()

        if (chapter.date_upload > 0) {
            descriptions.add(adapter.dateFormat.format(Date(chapter.date_upload)))
        }
        if (!chapter.read && chapter.last_page_read > 0) {
            descriptions.add(itemView.context.getString(R.string.chapter_progress, chapter.last_page_read + 1))
        }
        if (!chapter.scanlator.isNullOrBlank()) {
            descriptions.add(chapter.scanlator!!)
        }

        if (descriptions.isNotEmpty()) {
            chapter_description.text = descriptions.joinToString(" • ")
        } else {
            chapter_description.text = ""
        }

        notifyStatus(item.status)
    }

    fun notifyStatus(status: Int) = with(download_text) {
        when (status) {
            Download.QUEUE -> setText(R.string.chapter_queued)
            Download.DOWNLOADING -> setText(R.string.chapter_downloading)
            Download.DOWNLOADED -> setText(R.string.chapter_downloaded)
            Download.ERROR -> setText(R.string.chapter_error)
            else -> text = ""
        }
    }

    private fun showPopupMenu(view: View) {
        val item = adapter.getItem(adapterPosition) ?: return

        // Create a PopupMenu, giving it the clicked view for an anchor
        val popup = PopupMenu(view.context, view)

        // Inflate our menu resource into the PopupMenu's Menu
        popup.menuInflater.inflate(R.menu.chapter_single, popup.menu)

        val chapter = item.chapter

        // Hide download and show delete if the chapter is downloaded
        if (item.isDownloaded) {
            popup.menu.findItem(R.id.action_download).isVisible = false
            popup.menu.findItem(R.id.action_delete).isVisible = true
        }

        // Hide bookmark if bookmark
        popup.menu.findItem(R.id.action_bookmark).isVisible = !chapter.bookmark
        popup.menu.findItem(R.id.action_remove_bookmark).isVisible = chapter.bookmark

        // Hide mark as unread when the chapter is unread
        if (!chapter.read && chapter.last_page_read == 0) {
            popup.menu.findItem(R.id.action_mark_as_unread).isVisible = false
        }

        // Hide mark as read when the chapter is read
        if (chapter.read) {
            popup.menu.findItem(R.id.action_mark_as_read).isVisible = false
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            adapter.menuItemListener.onMenuItemClick(adapterPosition, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }
}
