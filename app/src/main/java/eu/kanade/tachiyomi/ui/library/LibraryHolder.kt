package eu.kanade.tachiyomi.ui.library

import android.view.View
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.source.base.Source
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder
import kotlinx.android.synthetic.main.item_catalogue_grid.view.*

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_catalogue_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new library holder.
 */
class LibraryHolder(view: View, adapter: LibraryCategoryAdapter, listener: FlexibleViewHolder.OnListItemClickListener) :
        FlexibleViewHolder(view, adapter, listener) {

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     * @param presenter the library presenter.
     */
    fun onSetValues(manga: Manga, presenter: LibraryPresenter) {
        // Update the title of the manga.
        itemView.title.text = manga.title

        // Update the unread count and its visibility.
        with(itemView.unreadText) {
            visibility = if (manga.unread > 0) View.VISIBLE else View.GONE
            text = manga.unread.toString()
        }

        // Update the cover.
        loadCover(manga, presenter.sourceManager.get(manga.source)!!, presenter.coverCache)
    }

    /**
     * Load the cover of a manga in a image view.
     *
     * @param manga the manga to bind.
     * @param source the source of the manga.
     * @param coverCache the cache that stores the cover in the filesystem.
     */
    private fun loadCover(manga: Manga, source: Source, coverCache: CoverCache) {
        if (manga.thumbnail_url != null) {
            coverCache.saveOrLoadFromCache(itemView.thumbnail, manga.thumbnail_url, source.glideHeaders)
        } else {
            itemView.thumbnail.setImageResource(android.R.color.transparent)
        }
    }

}
