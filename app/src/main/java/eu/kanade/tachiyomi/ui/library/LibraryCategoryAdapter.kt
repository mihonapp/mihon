package eu.kanade.tachiyomi.ui.library

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import eu.davidea.flexibleadapter4.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.EHentaiMetadata
import eu.kanade.tachiyomi.util.inflate
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import exh.isLewdSource
import exh.metadata.MetadataHelper
import exh.search.SearchEngine
import kotlinx.android.synthetic.main.item_catalogue_grid.view.*
import uy.kohesive.injekt.injectLazy
import java.util.*

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param fragment the fragment containing this adapter.
 */
class LibraryCategoryAdapter(val fragment: LibraryCategoryView) :
        FlexibleAdapter<LibraryHolder, Manga>() {

    /**
     * The list of manga in this category.
     */
    private var mangas: List<Manga> = emptyList()

    private val sourceManager: SourceManager by injectLazy()

    private val searchEngine = SearchEngine()
    private val metadataHelper = MetadataHelper()

    var asyncSearchText: String? = null

    init {
        setHasStableIds(true)
    }

    /**
     * Sets a list of manga in the adapter.
     *
     * @param list the list to set.
     */
    fun setItems(list: List<Manga>) {
        mItems = list

        // A copy of manga always unfiltered.
        mangas = ArrayList(list)
        updateDataSet(null)
    }

    /**
     * Returns the identifier for a manga.
     *
     * @param position the position in the adapter.
     * @return an identifier for the item.
     */
    override fun getItemId(position: Int): Long {
        return mItems[position].id!!
    }

    /**
     * Filters the list of manga applying [filterObject] for each element.
     *
     * @param param the filter. Not used.
     */
    override fun updateDataSet(param: String?) {
        //Async search filter (EH)
        val filtered = asyncSearchText?.let { search ->
            mangas.filter {
                filterObject(it, search)
            }
        } ?: mangas
        //The rest of the filters run on the main loop
        Handler(Looper.getMainLooper()).post {
            filterItems(filtered)
            notifyDataSetChanged()
        }
    }

    /**
     * Filters a manga depending on a query.
     *
     * @param manga the manga to filter.
     * @param query the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    override fun filterObject(manga: Manga, query: String): Boolean = with(manga) {
        if(!isLewdSource(manga.source)) {
            //Regular searching for normal manga
            title.toLowerCase().contains(query) ||
                    author != null && author!!.toLowerCase().contains(query)
        } else {
            //Use gallery search engine for EH manga
            val source = sourceManager.get(manga.source)
            source?.let {
                val exh: Boolean
                if(source is EHentai)
                    exh = source.exh
                else if(source is EHentaiMetadata)
                    exh = source.exh
                else
                    return@with false

                val metadata = metadataHelper.fetchMetadata(manga.url, exh)
                metadata?.let {
                    searchEngine.matches(metadata, searchEngine.parseQuery(query))
                } ?: title.contains(query, ignoreCase = true) //Use regular searching when the metadata is not set up for this gallery
            } ?: false
        }
    }

    /**
     * Creates a new view holder.
     *
     * @param parent the parent view.
     * @param viewType the type of the holder.
     * @return a new view holder for a manga.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryHolder {
        // Depending on preferences, display a list or display a grid
        if (parent is AutofitRecyclerView) {
            val view = parent.inflate(R.layout.item_catalogue_grid).apply {
                val coverHeight = parent.itemWidth / 3 * 4
                card.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, coverHeight)
                gradient.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, coverHeight / 2, Gravity.BOTTOM)
            }
            return LibraryGridHolder(view, this, fragment)
        } else {
            val view = parent.inflate(R.layout.item_catalogue_list)
            return LibraryListHolder(view, this, fragment)
        }
    }

    /**
     * Binds a holder with a new position.
     *
     * @param holder the holder to bind.
     * @param position the position to bind.
     */
    override fun onBindViewHolder(holder: LibraryHolder, position: Int) {
        val manga = getItem(position)

        holder.onSetValues(manga)
        // When user scrolls this bind the correct selection status
        holder.itemView.isActivated = isSelected(position)
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return mangas.orEmpty().indexOfFirst { it.id == manga.id }
    }

}
