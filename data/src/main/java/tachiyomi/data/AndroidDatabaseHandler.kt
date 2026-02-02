package tachiyomi.data

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AndroidDatabaseHandler(
    val db: Database,
    private val driver: SqlDriver,
    val queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val transactionDispatcher: CoroutineDispatcher = queryDispatcher,
) : DatabaseHandler {

    val suspendingTransactionId = ThreadLocal<Int>()

    override suspend fun <T> await(inTransaction: Boolean, block: suspend Database.() -> T): T {
        return dispatch(inTransaction, block)
    }

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend Database.() -> Query<T>,
    ): List<T> {
        return dispatch(inTransaction) { block(db).executeAsList() }
    }

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend Database.() -> Query<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend Database.() -> ExecutableQuery<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend Database.() -> Query<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend Database.() -> ExecutableQuery<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    override fun <T : Any> subscribeToList(block: Database.() -> Query<T>): Flow<List<T>> {
        return block(db).asFlow().mapToList(queryDispatcher)
    }

    override fun <T : Any> subscribeToOne(block: Database.() -> Query<T>): Flow<T> {
        return block(db).asFlow().mapToOne(queryDispatcher)
    }

    override fun <T : Any> subscribeToOneOrNull(block: Database.() -> Query<T>): Flow<T?> {
        return block(db).asFlow().mapToOneOrNull(queryDispatcher)
    }

    override fun <T : Any> subscribeToPagingSource(
        countQuery: Database.() -> Query<Long>,
        queryProvider: Database.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T> {
        return QueryPagingSource(
            handler = this,
            countQuery = countQuery,
            queryProvider = { limit, offset ->
                queryProvider.invoke(db, limit, offset)
            },
        )
    }

    override suspend fun vacuum() {
        withContext(queryDispatcher) {
            driver.execute(null, "VACUUM", 0, null)
            // Truncate WAL file after vacuum to reclaim disk space
            driver.execute(null, "PRAGMA wal_checkpoint(TRUNCATE)", 0, null)
        }
    }

    override suspend fun reindex() {
        withContext(queryDispatcher) {
            driver.execute(null, "REINDEX", 0, null)
        }
    }

    override suspend fun analyze() {
        withContext(queryDispatcher) {
            driver.execute(null, "ANALYZE", 0, null)
        }
    }

    override suspend fun getDatabaseStats(): Map<String, Long> {
        return withContext(queryDispatcher) {
            val stats = mutableMapOf<String, Long>()
            
            // Get page size
            driver.executeQuery(null, "PRAGMA page_size", { cursor ->
                if (cursor.next().value) {
                    stats["page_size"] = cursor.getLong(0) ?: 4096L
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0, null)
            
            // Get page count
            driver.executeQuery(null, "PRAGMA page_count", { cursor ->
                if (cursor.next().value) {
                    stats["page_count"] = cursor.getLong(0) ?: 0L
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0, null)
            
            // Get freelist count (unused pages)
            driver.executeQuery(null, "PRAGMA freelist_count", { cursor ->
                if (cursor.next().value) {
                    stats["freelist_count"] = cursor.getLong(0) ?: 0L
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0, null)
            
            // Calculate total size
            val pageSize = stats["page_size"] ?: 4096L
            val pageCount = stats["page_count"] ?: 0L
            stats["total_size_bytes"] = pageSize * pageCount
            
            stats
        }
    }
    
    /**
     * Get detailed database statistics including per-table and per-index sizes.
     * Useful for diagnosing what's consuming database space.
     */
    override suspend fun getDetailedDatabaseStats(): Map<String, Any> {
        return withContext(queryDispatcher) {
            val result = mutableMapOf<String, Any>()
            var actualPageSize = 4096L
            
            // Get page size first
            driver.executeQuery(null, "PRAGMA page_size", { cursor ->
                if (cursor.next().value) {
                    actualPageSize = cursor.getLong(0) ?: 4096L
                    result["page_size"] = actualPageSize
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0, null)
            
            // Get overall stats
            driver.executeQuery(null, "PRAGMA page_count", { cursor ->
                if (cursor.next().value) {
                    val count = cursor.getLong(0) ?: 0L
                    result["page_count"] = count
                    result["total_size_bytes"] = actualPageSize * count
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0, null)
            
            driver.executeQuery(null, "PRAGMA freelist_count", { cursor ->
                if (cursor.next().value) {
                    val count = cursor.getLong(0) ?: 0L
                    result["freelist_count"] = count
                    result["freelist_size_bytes"] = actualPageSize * count
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0, null)
            
            // Get row counts per table
            val tableCounts = mutableMapOf<String, Long>()
            listOf("chapters", "mangas", "history", "library_cache", "categories", "mangas_categories", "manga_sync", "excluded_scanlators", "translated_chapters").forEach { table ->
                try {
                    driver.executeQuery(null, "SELECT count(*) FROM $table", { cursor ->
                        if (cursor.next().value) {
                            tableCounts[table] = cursor.getLong(0) ?: 0L
                        }
                        app.cash.sqldelight.db.QueryResult.Unit
                    }, 0, null)
                } catch (e: Exception) {
                    tableCounts[table] = -1L // Table might not exist
                }
            }
            result["table_row_counts"] = tableCounts
            
            // Estimate table sizes using dbstat if available (SQLite 3.31+)
            val tableSizes = mutableMapOf<String, Long>()
            val indexSizes = mutableMapOf<String, Long>()
            try {
                driver.executeQuery(null, 
                    "SELECT name, SUM(pgsize) as size FROM dbstat GROUP BY name ORDER BY size DESC", 
                    { cursor ->
                        while (cursor.next().value) {
                            val name = cursor.getString(0) ?: continue
                            val size = cursor.getLong(1) ?: 0L
                            if (name.contains("_index") || name.startsWith("sqlite_autoindex")) {
                                indexSizes[name] = size
                            } else {
                                tableSizes[name] = size
                            }
                        }
                        app.cash.sqldelight.db.QueryResult.Unit
                    }, 0, null)
                result["table_sizes_bytes"] = tableSizes
                result["index_sizes_bytes"] = indexSizes
            } catch (e: Exception) {
                // dbstat virtual table not available, skip detailed sizing
                result["dbstat_available"] = false
            }
            
            // Get WAL size info
            driver.executeQuery(null, "PRAGMA wal_checkpoint", { cursor ->
                if (cursor.next().value) {
                    result["wal_frames_total"] = cursor.getLong(1) ?: 0L
                    result["wal_frames_checkpointed"] = cursor.getLong(2) ?: 0L
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0, null)
            
            // Average chapter data size estimate
            val chapterCount = tableCounts["chapters"] ?: 0L
            if (chapterCount > 0) {
                driver.executeQuery(null, 
                    "SELECT AVG(length(url) + length(name) + coalesce(length(scanlator), 0)) FROM chapters LIMIT 1000", 
                    { cursor ->
                        if (cursor.next().value) {
                            result["avg_chapter_text_bytes"] = cursor.getDouble(0) ?: 0.0
                        }
                        app.cash.sqldelight.db.QueryResult.Unit
                    }, 0, null)
            }
            
            // Average manga description size
            val mangaCount = tableCounts["mangas"] ?: 0L
            if (mangaCount > 0) {
                driver.executeQuery(null, 
                    "SELECT AVG(coalesce(length(description), 0)) FROM mangas", 
                    { cursor ->
                        if (cursor.next().value) {
                            result["avg_manga_description_bytes"] = cursor.getDouble(0) ?: 0.0
                        }
                        app.cash.sqldelight.db.QueryResult.Unit
                    }, 0, null)
            }
            
            result
        }
    }

    private suspend fun <T> dispatch(inTransaction: Boolean, block: suspend Database.() -> T): T {
        // Create a transaction if needed and run the calling block inside it.
        if (inTransaction) {
            return withTransaction { block(db) }
        }

        // If we're currently in the transaction thread, there's no need to dispatch our query.
        if (driver.currentTransaction() != null) {
            return block(db)
        }

        // Get the current database context and run the calling block.
        val context = getCurrentDatabaseContext()
        return withContext(context) { block(db) }
    }
}
