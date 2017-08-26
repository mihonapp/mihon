package eu.kanade.tachiyomi.ui.library

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.*
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.NHentaiMetadata
import exh.metadata.models.PervEdenGalleryMetadata
import exh.metadata.models.SearchableGalleryMetadata
import exh.metadata.queryMetadataFromManga
import exh.metadata.syncMangaIds
import exh.search.SearchEngine
import exh.util.defRealm
import io.realm.RealmResults
import timber.log.Timber
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
            Thread.sleep(1000)
            defRealm {
                it.syncMangaIds(list.map {
                    it.manga
                })
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
        return mangas.indexOfFirst { it.manga.id == manga.id }
    }

    fun performFilter() {
        if(searchText.isNotBlank()) {
            try {
                val parsedQuery = searchEngine.parseQuery(searchText)
                val metadata = view.meta!!.map {
                    val meta: RealmResults<out SearchableGalleryMetadata> = if (it.value.isNotEmpty())
                        searchEngine.filterResults(it.value.where(),
                                parsedQuery,
                                it.value.first().titleFields)
                                .findAllSorted(SearchableGalleryMetadata::mangaId.name)
                    else
                        it.value
                    Pair(it.key, meta)
                }.toMap()
                // --> Possible data set compare algorithm?
//            var curUnfilteredMetaIndex = 0
//            var curFilteredMetaIndex = 0
//            val res = mangas.sortedBy { it.manga.id }.filter { manga ->
                val res = mangas.filter { manga ->
                    // --> EH
                    try {
                        if (isLewdSource(manga.manga.source)) {
                            val unfilteredMeta: RealmResults<out SearchableGalleryMetadata>?
                            val filteredMeta: RealmResults<out SearchableGalleryMetadata>?
                            when (manga.manga.source) {
                                EH_SOURCE_ID,
                                EXH_SOURCE_ID -> {
                                    unfilteredMeta = view.meta!![ExGalleryMetadata::class]
                                    filteredMeta = metadata[ExGalleryMetadata::class]
                                }
                                PERV_EDEN_IT_SOURCE_ID,
                                PERV_EDEN_EN_SOURCE_ID -> {
                                    unfilteredMeta = view.meta!![PervEdenGalleryMetadata::class]
                                    filteredMeta = metadata[PervEdenGalleryMetadata::class]
                                }
                                NHENTAI_SOURCE_ID -> {
                                    unfilteredMeta = view.meta!![NHentaiMetadata::class]
                                    filteredMeta = metadata[NHentaiMetadata::class]
                                }
                                else -> {
                                    unfilteredMeta = null
                                    filteredMeta = null
                                }
                            }

                            /*
                    --> Possible data set compare algorithm?

                    var atUnfilteredMeta = unfilteredMeta?.getOrNull(curUnfilteredMetaIndex)

                    while(atUnfilteredMeta?.mangaId != null
                            && atUnfilteredMeta.mangaId!! < manga.manga.id!!) {
                        curUnfilteredMetaIndex++
                        atUnfilteredMeta = unfilteredMeta?.getOrNull(curUnfilteredMetaIndex)
                    }

                    if(atUnfilteredMeta?.mangaId == manga.manga.id) {
                        var atFilteredMeta = filteredMeta?.getOrNull(curFilteredMetaIndex)
                        while(atFilteredMeta?.mangaId != null
                                && atFilteredMeta.mangaId!! < manga.manga.id!!) {
                            curFilteredMetaIndex++
                            atFilteredMeta = filteredMeta?.getOrNull(curFilteredMetaIndex)
                        }

                        return@filter atFilteredMeta?.mangaId == manga.manga.id
                    }*/
                            val hasMeta = unfilteredMeta
                                    ?.where()
                                    ?.equalTo(SearchableGalleryMetadata::mangaId.name, manga.manga.id)
                                    ?.count() ?: 0 > 0
                            if (hasMeta)
                                return@filter filteredMeta!!.where()
                                        .equalTo(SearchableGalleryMetadata::mangaId.name, manga.manga.id)
                                        .count() > 0
                        }
                    } catch(e: Exception) {
                        Timber.w(e, "Could not filter manga!", manga.manga)
                    }
                    manga.filter(searchText)
                    // <-- EH
                }
                updateDataSet(res)
            } catch(e: Exception) {
                Timber.w(e, "Could not filter mangas!")
                updateDataSet(mangas)
            }
        } else {
            updateDataSet(mangas)
        }
    }

}
