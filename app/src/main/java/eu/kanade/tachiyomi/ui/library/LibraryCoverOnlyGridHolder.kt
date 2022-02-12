package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.clear
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SourceCoverOnlyGridItemBinding
import eu.kanade.tachiyomi.util.view.loadAnyAutoPause

class LibraryCoverOnlyGridHolder(
    view: View,
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
) : LibraryHolder<SourceCoverOnlyGridItemBinding>(view, adapter) {

    override val binding = SourceCoverOnlyGridItemBinding.bind(view)

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {
        // For rounded corners
        binding.badges.leftBadges.clipToOutline = true
        binding.badges.rightBadges.clipToOutline = true

        // Update the unread count and its visibility.
        with(binding.badges.unreadText) {
            isVisible = item.unreadCount > 0
            text = item.unreadCount.toString()
        }
        // Update the download count and its visibility.
        with(binding.badges.downloadText) {
            isVisible = item.downloadCount > 0
            text = item.downloadCount.toString()
        }
        // Update the source language and its visibility
        with(binding.badges.languageText) {
            isVisible = item.sourceLanguage.isNotEmpty()
            text = item.sourceLanguage
        }
        // set local visibility if its local manga
        binding.badges.localText.isVisible = item.isLocal

        // For rounded corners
        binding.card.clipToOutline = true

        // Update the cover.
        binding.thumbnail.clear()
        if (!item.manga.thumbnail_url.isNullOrEmpty()) {
            binding.thumbnail.loadAnyAutoPause(item.manga)
        } else {
            // Set manga title
            binding.title.text = item.manga.title
        }
    }
}
