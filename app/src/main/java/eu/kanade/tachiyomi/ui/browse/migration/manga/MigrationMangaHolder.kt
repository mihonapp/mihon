package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.view.View
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.databinding.SourceListItemBinding

class MigrationMangaHolder(
    view: View,
    private val adapter: MigrationMangaAdapter
) : FlexibleViewHolder(view, adapter) {

    private val binding = SourceListItemBinding.bind(view)

    init {
        binding.thumbnail.setOnClickListener {
            adapter.coverClickListener.onCoverClick(bindingAdapterPosition)
        }
    }

    fun bind(item: MigrationMangaItem) {
        binding.title.text = item.manga.title

        // Update the cover.
        GlideApp.with(itemView.context).clear(binding.thumbnail)

        val radius = itemView.context.resources.getDimensionPixelSize(R.dimen.card_radius)
        val requestOptions = RequestOptions().transform(CenterCrop(), RoundedCorners(radius))
        GlideApp.with(itemView.context)
            .load(item.manga.toMangaThumbnail())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .apply(requestOptions)
            .dontAnimate()
            .into(binding.thumbnail)
    }
}
