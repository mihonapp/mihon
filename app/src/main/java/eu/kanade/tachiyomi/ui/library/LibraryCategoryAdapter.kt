package eu.kanade.tachiyomi.ui.library

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.*
import exh.metadata.metadataClass
import exh.metadata.models.SearchableGalleryMetadata
import exh.metadata.syncMangaIds
import exh.search.SearchEngine
import exh.util.defRealm
import io.realm.RealmResults
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(val view: LibraryCategoryView) :
        FlexibleAdapter<LibraryItem>(null, view, true) {
    // --> EH
    private val searchEngine = SearchEngine()
    // <-- EH

    /**
     * The list of manga in this category.
     */
    private var mangas: List<LibraryItem> = emptyList()

    /**
     * Sets a list of manga in the adapter.
     *
     * @param list the list to set.
     */
    fun setItems(list: List<LibraryItem>) {
        // A copy of manga always unfiltered.
        mangas = list.toList()

        // Sync manga IDs in background (EH)
        thread {
            //Wait 1s to reduce UI stutter during animations
            Thread.sleep(2000)
            defRealm {
                it.syncMangaIds(mangas)
            }
        }

        performFilter()
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return currentItems.indexOfFirst { it.manga.id == manga.id }
    }

    fun performFilter() {
        if(searchText.isNotBlank()) {
            if(cacheText != searchText) {
                globalSearchCache.clear()
                cacheText = searchText
            }

            try {
                val thisCache = globalSearchCache.getOrPut(view.category.name) {
                    SearchCache(mangas.size)
                }

                if(thisCache.ready) {
                    //Skip everything if cache matches our query exactly
                    updateDataSet(mangas.filter {
                        thisCache.cache[it.manga.id] ?: false
                    })
                } else {
                    thisCache.cache.clear()

                    val parsedQuery = searchEngine.parseQuery(searchText)
                    var totalFilteredSize = 0

                    val metadata = view.controller.meta!!.map {
                        val meta: RealmResults<out SearchableGalleryMetadata> = if (it.value.isNotEmpty())
                            searchEngine.filterResults(it.value.where(),
                                    parsedQuery,
                                    it.value.first()!!.titleFields)
                                    .sort(SearchableGalleryMetadata::mangaId.name)
                                    .findAll().apply {
                                totalFilteredSize += size
                            }
                        else
                            it.value
                        Pair(it.key, meta)
                    }.toMap()

                    val out = ArrayList<LibraryItem>(mangas.size)

                    var lewdMatches = 0

                    for(manga in mangas) {
                        // --> EH
                        try {
                            if (isLewdSource(manga.manga.source)) {
                                //Stop matching lewd manga if we have matched them all already!
                                if (lewdMatches >= totalFilteredSize)
                                    continue

                                val metaClass = manga.manga.metadataClass
                                val unfilteredMeta = view.controller.meta!![metaClass]
                                val filteredMeta = metadata[metaClass]

                                val hasMeta = manga.hasMetadata ?: (unfilteredMeta
                                        ?.where()
                                        ?.equalTo(SearchableGalleryMetadata::mangaId.name, manga.manga.id)
                                        ?.count() ?: 0 > 0)

                                if (hasMeta) {
                                    if (filteredMeta!!.where()
                                            .equalTo(SearchableGalleryMetadata::mangaId.name, manga.manga.id)
                                            .count() > 0) {
                                        //Metadata match!
                                        lewdMatches++
                                        thisCache.cache[manga.manga.id!!] = true
                                        out += manga
                                        continue
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Could not filter manga! %s", manga.manga)
                        }

                        //Fallback to regular filter
                        val filterRes = manga.filter(searchText)
                        thisCache.cache[manga.manga.id!!] = filterRes
                        if(filterRes) out += manga
                        // <-- EH
                    }
                    thisCache.ready = true
                    updateDataSet(out)
                }
            } catch(e: Exception) {
                Timber.w(e, "Could not filter mangas!")
                updateDataSet(mangas)
            }
        } else {
            globalSearchCache.clear()
            updateDataSet(mangas)
        }
    }

    class SearchCache(size: Int) {
        var ready = false
        var cache = HashMap<Long, Boolean>(size)
    }

    companion object {
        var cacheText: String? = null
        val globalSearchCache = ConcurrentHashMap<String, SearchCache>()
    }
}
