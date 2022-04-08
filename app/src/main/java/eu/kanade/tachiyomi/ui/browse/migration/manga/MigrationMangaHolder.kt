package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.view.View
import coil.dispose
import coil.load
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.SourceListItemBinding

class MigrationMangaHolder(
    view: View,
    private val adapter: MigrationMangaAdapter,
) : FlexibleViewHolder(view, adapter) {

    private val binding = SourceListItemBinding.bind(view)

    init {
        binding.thumbnail.setOnClickListener {
            adapter.coverClickListener.onCoverClick(bindingAdapterPosition)
        }
    }

    fun bind(item: MigrationMangaItem) {
        binding.title.text = item.manga.title

        // Update the cover
        binding.thumbnail.dispose()
        binding.thumbnail.load(item.manga)
    }
}
