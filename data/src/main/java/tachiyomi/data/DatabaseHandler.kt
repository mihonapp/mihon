package tachiyomi.data

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import kotlinx.coroutines.flow.Flow

interface DatabaseHandler {

    suspend fun <T> await(inTransaction: Boolean = false, block: suspend Database.() -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>,
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>,
    ): T

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean = false,
        block: suspend Database.() -> ExecutableQuery<T>,
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>,
    ): T?

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean = false,
        block: suspend Database.() -> ExecutableQuery<T>,
    ): T?

    fun <T : Any> subscribeToList(block: Database.() -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: Database.() -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: Database.() -> Query<T>): Flow<T?>

    fun <T : Any> subscribeToPagingSource(
        countQuery: Database.() -> Query<Long>,
        queryProvider: Database.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T>

    /**
     * Execute VACUUM to rebuild the database file, reclaiming unused space.
     * This can significantly reduce database size after deleting many entries.
     * WARNING: This operation can take a long time on large databases and requires
     * free disk space equal to the database size.
     */
    suspend fun vacuum()

    /**
     * Execute REINDEX to rebuild all indexes.
     * This can improve query performance if indexes have become fragmented.
     */
    suspend fun reindex()

    /**
     * Execute ANALYZE to update database statistics used by the query planner.
     * This can improve query performance by helping SQLite choose better query plans.
     */
    suspend fun analyze()

    /**
     * Get database statistics including size, page count, and freelist count.
     * @return Map with keys: page_size, page_count, freelist_count, total_size_bytes
     */
    suspend fun getDatabaseStats(): Map<String, Long>
    
    /**
     * Get detailed database statistics including per-table and per-index sizes.
     * Useful for diagnosing what's consuming database space.
     * @return Map with detailed statistics including table_row_counts, table_sizes_bytes, index_sizes_bytes
     */
    suspend fun getDetailedDatabaseStats(): Map<String, Any>
}
