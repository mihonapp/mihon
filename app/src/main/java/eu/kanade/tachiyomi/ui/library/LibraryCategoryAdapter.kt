package eu.kanade.tachiyomi.ui.library

import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.isLewdSource
import exh.metadata.sql.tables.SearchMetadataTable
import exh.search.SearchEngine
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(val view: LibraryCategoryView) :
        FlexibleAdapter<LibraryItem>(null, view, true) {
    // --> EH
    private val db: DatabaseHelper by injectLazy()
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
            // EXH -->
            try {
                val startTime = System.currentTimeMillis()

                val parsedQuery = searchEngine.parseQuery(searchText)
                val sqlQuery = searchEngine.queryToSql(parsedQuery)
                val queryResult = db.lowLevel().rawQuery(RawQuery.builder()
                        .query(sqlQuery.first)
                        .args(*sqlQuery.second.toTypedArray())
                        .build())

                val convertedResult = ArrayList<Long>(queryResult.count)
                val mangaIdCol = queryResult.getColumnIndex(SearchMetadataTable.COL_MANGA_ID)
                queryResult.moveToFirst()
                while(queryResult.count > 0 && !queryResult.isAfterLast) {
                    convertedResult += queryResult.getLong(mangaIdCol)
                    queryResult.moveToNext()
                }

                val out = mangas.filter {
                    if(isLewdSource(it.manga.source)) {
                        convertedResult.binarySearch(it.manga.id) >= 0
                    } else {
                        it.filter(searchText)
                    }
                }

                Timber.d("===> Took %s milliseconds to filter manga!", System.currentTimeMillis() - startTime)

                updateDataSet(out)
            } catch(e: Exception) {
                Timber.w(e, "Could not filter mangas!")
                updateDataSet(mangas)
            }
            // EXH <--
        } else {
            updateDataSet(mangas)
        }
    }
}
