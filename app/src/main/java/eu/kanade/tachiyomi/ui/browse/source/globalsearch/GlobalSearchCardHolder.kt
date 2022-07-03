package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import android.view.View
import androidx.core.view.isVisible
import coil.dispose
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.databinding.GlobalSearchControllerCardItemBinding
import eu.kanade.tachiyomi.util.view.loadAutoPause

class GlobalSearchCardHolder(view: View, adapter: GlobalSearchCardAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = GlobalSearchControllerCardItemBinding.bind(view)

    init {
        // Call onMangaClickListener when item is pressed.
        itemView.setOnClickListener {
            val item = adapter.getItem(bindingAdapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaClick(item.manga)
            }
        }
        itemView.setOnLongClickListener {
            val item = adapter.getItem(bindingAdapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaLongClick(item.manga)
            }
            true
        }
    }

    fun bind(manga: Manga) {
        binding.card.clipToOutline = true

        // Set manga title
        binding.title.text = manga.title

        // Set alpha of thumbnail.
        binding.cover.alpha = if (manga.favorite) 0.3f else 1.0f

        // For rounded corners
        binding.badges.clipToOutline = true

        // Set favorite badge
        binding.favoriteText.isVisible = manga.favorite

        setImage(manga)
    }

    fun setImage(manga: Manga) {
        binding.cover.dispose()
        binding.cover.loadAutoPause(manga) {
            setParameter(MangaCoverFetcher.USE_CUSTOM_COVER, false)
        }
    }
}
