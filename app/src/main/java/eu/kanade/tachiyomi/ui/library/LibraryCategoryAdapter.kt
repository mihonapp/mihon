package eu.kanade.tachiyomi.ui.library

import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.isLewdSource
import exh.metadata.sql.tables.SearchMetadataTable
import exh.search.SearchEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(val view: LibraryCategoryView) :
        FlexibleAdapter<LibraryItem>(null, view, true) {
    // EXH -->
    private val db: DatabaseHelper by injectLazy()
    private val searchEngine = SearchEngine()
    private var lastFilterJob: Job? = null

    // Keep compatibility as searchText field was replaced when we upgraded FlexibleAdapter
    var searchText
        get() = getFilter(String::class.java) ?: ""
        set(value) { setFilter(value) }
    // EXH <--

    /**
     * The list of manga in this category.
     */
    private var mangas: List<LibraryItem> = emptyList()

    /**
     * Sets a list of manga in the adapter.
     *
     * @param list the list to set.
     */
    suspend fun setItems(cScope: CoroutineScope, list: List<LibraryItem>) {
        // A copy of manga always unfiltered.
        mangas = list.toList()

        performFilter(cScope)
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return currentItems.indexOfFirst { it.manga.id == manga.id }
    }

    // EXH -->
    // Note that we cannot use FlexibleAdapter's built in filtering system as we cannot cancel it
    //   (well technically we can cancel it by invoking filterItems again but that doesn't work when
    //    we want to perform a no-op filter)
    suspend fun performFilter(cScope: CoroutineScope) {
        lastFilterJob?.cancel()
        if(mangas.isNotEmpty() && searchText.isNotBlank()) {
            val savedSearchText = searchText

            val job = cScope.launch(Dispatchers.IO) {
                val newManga = try {
                    // Prepare filter object
                    val parsedQuery = searchEngine.parseQuery(savedSearchText)
                    val sqlQuery = searchEngine.queryToSql(parsedQuery)
                    val queryResult = db.lowLevel().rawQuery(RawQuery.builder()
                            .query(sqlQuery.first)
                            .args(*sqlQuery.second.toTypedArray())
                            .build())

                    if(!isActive) return@launch // Fail early when cancelled

                    val convertedResult = LongArray(queryResult.count)
                    if(convertedResult.isNotEmpty()) {
                        val mangaIdCol = queryResult.getColumnIndex(SearchMetadataTable.COL_MANGA_ID)
                        queryResult.moveToFirst()
                        while (!queryResult.isAfterLast) {
                            if(!isActive) return@launch // Fail early when cancelled

                            convertedResult[queryResult.position] = queryResult.getLong(mangaIdCol)
                            queryResult.moveToNext()
                        }
                    }

                    if(!isActive) return@launch // Fail early when cancelled

                    // Flow the mangas to allow cancellation of this filter operation
                    mangas.asFlow().filter { item ->
                        if(isLewdSource(item.manga.source)) {
                            convertedResult.binarySearch(item.manga.id ?: -1) >= 0
                        } else {
                            item.filter(savedSearchText)
                        }
                    }.toList()
                } catch (e: Exception) {
                    // Do not catch cancellations
                    if(e is CancellationException) throw e

                    Timber.w(e, "Could not filter mangas!")
                    mangas
                }

                withContext(Dispatchers.Main) {
                    updateDataSet(newManga)
                }
            }
            lastFilterJob = job
            job.join()
        } else {
            updateDataSet(mangas)
        }
    }
    // EXH <--
}
