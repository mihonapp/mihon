package eu.kanade.tachiyomi.ui.recent.updates

import android.view.View
import androidx.core.view.isVisible
import coil.dispose
import coil.load
import eu.kanade.tachiyomi.databinding.UpdatesItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.chapter.base.BaseChapterHolder

/**
 * Holder that contains chapter item
 * UI related actions should be called from here.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new recent chapter holder.
 */
class UpdatesHolder(private val view: View, private val adapter: UpdatesAdapter) :
    BaseChapterHolder(view, adapter) {

    private val binding = UpdatesItemBinding.bind(view)

    init {
        binding.mangaCover.setOnClickListener {
            adapter.coverClickListener.onCoverClick(bindingAdapterPosition)
        }

        binding.download.setOnClickListener {
            onDownloadClick(it, bindingAdapterPosition)
        }
        binding.download.setOnLongClickListener {
            onDownloadLongClick(bindingAdapterPosition)
            true
        }
    }

    fun bind(item: UpdatesItem) {
        // Set chapter title
        binding.chapterTitle.text = item.chapter.name

        // Set manga title
        binding.mangaTitle.text = item.manga.title

        // Check if chapter is read and/or bookmarked and set correct color
        if (item.chapter.read) {
            binding.chapterTitle.setTextColor(adapter.readColor)
            binding.mangaTitle.setTextColor(adapter.readColor)
        } else {
            binding.mangaTitle.setTextColor(adapter.unreadColor)
            binding.chapterTitle.setTextColor(
                if (item.bookmark) adapter.bookmarkedColor else adapter.unreadColorSecondary,
            )
        }

        // Set bookmark status
        binding.bookmarkIcon.isVisible = item.bookmark

        // Set chapter status
        binding.download.isVisible = item.manga.source != LocalSource.ID
        binding.download.setState(item.status, item.progress)

        // Set cover
        binding.mangaCover.dispose()
        binding.mangaCover.load(item.manga)
    }
}
