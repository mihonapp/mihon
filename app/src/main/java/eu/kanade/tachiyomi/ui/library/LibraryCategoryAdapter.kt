package eu.kanade.tachiyomi.ui.library

import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import exh.isLewdSource
import exh.metadata.sql.tables.SearchMetadataTable
import exh.search.SearchEngine
import exh.util.await
import exh.util.cancellable
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

                    ensureActive() // Fail early when cancelled

                    val mangaWithMetaIdsQuery = db.getIdsOfFavoriteMangaWithMetadata().await()
                    val mangaWithMetaIds = LongArray(mangaWithMetaIdsQuery.count)
                    if(mangaWithMetaIds.isNotEmpty()) {
                        val mangaIdCol = mangaWithMetaIdsQuery.getColumnIndex(MangaTable.COL_ID)
                        mangaWithMetaIdsQuery.moveToFirst()
                        while (!mangaWithMetaIdsQuery.isAfterLast) {
                            ensureActive() // Fail early when cancelled

                            mangaWithMetaIds[mangaWithMetaIdsQuery.position] = mangaWithMetaIdsQuery.getLong(mangaIdCol)
                            mangaWithMetaIdsQuery.moveToNext()
                        }
                    }

                    ensureActive() // Fail early when cancelled

                    val convertedResult = LongArray(queryResult.count)
                    if(convertedResult.isNotEmpty()) {
                        val mangaIdCol = queryResult.getColumnIndex(SearchMetadataTable.COL_MANGA_ID)
                        queryResult.moveToFirst()
                        while (!queryResult.isAfterLast) {
                            ensureActive() // Fail early when cancelled

                            convertedResult[queryResult.position] = queryResult.getLong(mangaIdCol)
                            queryResult.moveToNext()
                        }
                    }

                    ensureActive() // Fail early when cancelled

                    // Flow the mangas to allow cancellation of this filter operation
                    mangas.asFlow().cancellable().filter { item ->
                        if(isLewdSource(item.manga.source)) {
                            val mangaId = item.manga.id ?: -1
                            if(convertedResult.binarySearch(mangaId) < 0) {
                                // Check if this manga even has metadata
                                if(mangaWithMetaIds.binarySearch(mangaId) < 0) {
                                    // No meta? Filter using title
                                    item.filter(savedSearchText)
                                } else false
                            } else true
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
