package eu.kanade.tachiyomi.ui.library

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.models.Manga

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(view: LibraryCategoryView) :
        FlexibleAdapter<LibraryItem>(null, view, true) {

    /**
     * The list of manga in this category.
     */
    private var mangas: List<LibraryItem> = emptyList()

    //EH
    private val sourceManager: SourceManager by injectLazy()

    private val searchEngine = SearchEngine()
    private val metadataHelper = MetadataHelper()

    var asyncSearchText: String? = null

    /**
     * Sets a list of manga in the adapter.
     *
     * @param list the list to set.
     */
    fun setItems(list: List<LibraryItem>) {
        // A copy of manga always unfiltered.
        mangas = list.toList()

        performFilter()
    }

    // --> EH
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
            val metadata = metadataHelper.fetchMetadata(manga.url, manga.source)
            metadata?.let {
                searchEngine.matches(it, searchEngine.parseQuery(query))
            } ?: title.contains(query, ignoreCase = true) //Use regular searching when the metadata is not set up for this gallery
        }
    }

    // <-- EH

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return mangas.indexOfFirst { it.manga.id == manga.id }
    }

    fun performFilter() {
        updateDataSet(mangas.filter { it.filter(searchText) })
    }

}
