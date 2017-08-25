package eu.kanade.tachiyomi.ui.library

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.*
import exh.metadata.loadAllMetadata
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.NHentaiMetadata
import exh.metadata.models.PervEdenGalleryMetadata
import exh.metadata.queryMetadataFromManga
import exh.search.SearchEngine
import exh.util.defRealm
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import kotlin.concurrent.thread

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

    // --> EH
    private val searchEngine = SearchEngine()
    // <-- EH

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

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return mangas.indexOfFirst { it.manga.id == manga.id }
    }

    fun performFilter() {
        Observable.fromCallable {
            defRealm { realm ->
                val parsedQuery = searchEngine.parseQuery(searchText)
                val metadata = realm.loadAllMetadata().map {
                    Pair(it.key, searchEngine.filterResults(it.value, parsedQuery))
                }
                mangas.filter { manga ->
                    // --> EH
                    if (isLewdSource(manga.manga.source)) {
                        val hasMeta
                                = realm.queryMetadataFromManga(manga.manga).count() > 0
                        if(hasMeta)
                            metadata.any {
                                when (manga.manga.source) {
                                    EH_SOURCE_ID,
                                    EXH_SOURCE_ID ->
                                        if (it.first != ExGalleryMetadata::class)
                                            return@any false
                                    PERV_EDEN_IT_SOURCE_ID,
                                    PERV_EDEN_EN_SOURCE_ID ->
                                        if (it.first != PervEdenGalleryMetadata::class)
                                            return@any false
                                    NHENTAI_SOURCE_ID ->
                                        if (it.first != NHentaiMetadata::class)
                                            return@any false
                                }
                                return@filter realm.queryMetadataFromManga(manga.manga, it.second.where()).count() > 0
                            }
                    }
                    manga.filter(searchText)
                    // <-- EH
                }
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    updateDataSet(it)
                }
    }

}
