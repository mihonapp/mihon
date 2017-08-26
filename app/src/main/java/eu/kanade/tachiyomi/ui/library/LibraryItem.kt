package eu.kanade.tachiyomi.ui.library

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.inflate
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import exh.*
import exh.metadata.ehMetaQueryFromUrl
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.NHentaiMetadata
import exh.metadata.models.PervEdenGalleryMetadata
import exh.metadata.models.SearchableGalleryMetadata
import exh.metadata.nhentaiMetaQueryFromUrl
import exh.metadata.pervEdenMetaQueryFromUrl
import exh.search.SearchEngine
import exh.util.defRealm
import io.realm.RealmQuery
import kotlinx.android.synthetic.main.catalogue_grid_item.view.*

class LibraryItem(val manga: Manga) : AbstractFlexibleItem<LibraryHolder>(), IFilterable {
    // --> EH
    private val searchEngine = SearchEngine()
    // <-- EH

    override fun getLayoutRes(): Int {
        return R.layout.catalogue_grid_item
    }

    override fun createViewHolder(adapter: FlexibleAdapter<*>,
                                  inflater: LayoutInflater,
                                  parent: ViewGroup): LibraryHolder {

        return if (parent is AutofitRecyclerView) {
            val view = parent.inflate(R.layout.catalogue_grid_item).apply {
                val coverHeight = parent.itemWidth / 3 * 4
                card.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, coverHeight)
                gradient.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT, coverHeight / 2, Gravity.BOTTOM)
            }
            LibraryGridHolder(view, adapter)
        } else {
            val view = parent.inflate(R.layout.catalogue_list_item)
            LibraryListHolder(view, adapter)
        }
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<*>,
                                holder: LibraryHolder,
                                position: Int,
                                payloads: List<Any?>?) {

        holder.onSetValues(manga)
    }

    /**
     * Filters a manga depending on a query.
     *
     * @param constraint the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    override fun filter(constraint: String): Boolean {
        defRealm { realm ->
            if (isLewdSource(manga.source)) {
                val titleFields: List<String>?
                var query: RealmQuery<out SearchableGalleryMetadata>?
                when (manga.source) {
                    EH_SOURCE_ID -> {
                        titleFields = ExGalleryMetadata.TITLE_FIELDS
                        query = realm.ehMetaQueryFromUrl(manga.url, false)
                    }
                    EXH_SOURCE_ID -> {
                        titleFields = ExGalleryMetadata.TITLE_FIELDS
                        query = realm.ehMetaQueryFromUrl(manga.url, true)
                    }
                    PERV_EDEN_IT_SOURCE_ID,
                    PERV_EDEN_EN_SOURCE_ID -> {
                        titleFields = PervEdenGalleryMetadata.TITLE_FIELDS
                        query = realm.pervEdenMetaQueryFromUrl(manga.url, manga.source)
                    }
                    NHENTAI_SOURCE_ID -> {
                        titleFields = NHentaiMetadata.TITLE_FIELDS
                        query = realm.nhentaiMetaQueryFromUrl(manga.url)
                    }
                    else -> return@defRealm
                }
                val hasMeta = query!!.count() > 0
                if(hasMeta) {
                    val parsedQuery = searchEngine.parseQuery(constraint)
                    query = searchEngine.filterResults(query, parsedQuery, titleFields)
                    return@filter query.count() > 0
                }
            }
        }
        return manga.title.contains(constraint, true) ||
                (manga.author?.contains(constraint, true) ?: false)
    }

    override fun equals(other: Any?): Boolean {
        if (other is LibraryItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id!!.hashCode()
    }
}