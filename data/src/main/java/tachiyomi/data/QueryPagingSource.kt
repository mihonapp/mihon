package tachiyomi.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import kotlin.properties.Delegates

class QueryPagingSource<RowType : Any>(
    val handler: DatabaseHandler,
    val countQuery: Database.() -> Query<Long>,
    val queryProvider: Database.(Long, Long) -> Query<RowType>,
) : PagingSource<Long, RowType>(), Query.Listener {

    override val jumpingSupported: Boolean = true

    private var currentQuery: Query<RowType>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }

    init {
        registerInvalidatedCallback {
            currentQuery?.removeListener(this)
            currentQuery = null
        }
    }

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, RowType> {
        try {
            val key = params.key ?: 0L
            val loadSize = params.loadSize
            val count = handler.awaitOne { countQuery() }

            val (offset, limit) = when (params) {
                is LoadParams.Prepend -> key - loadSize to loadSize.toLong()
                else -> key to loadSize.toLong()
            }

            val data = handler.awaitList {
                queryProvider(limit, offset)
                    .also { currentQuery = it }
            }

            val (prevKey, nextKey) = when (params) {
                is LoadParams.Append -> (offset - loadSize to offset + loadSize)
                else -> (offset to offset + loadSize)
            }

            return LoadResult.Page(
                data = data,
                prevKey = if (offset <= 0L || prevKey < 0L) null else prevKey,
                nextKey = if (offset + loadSize >= count) null else nextKey,
                itemsBefore = maxOf(0L, offset).toInt(),
                itemsAfter = maxOf(0L, count - (offset + loadSize)).toInt(),
            )
        } catch (e: Exception) {
            return LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, RowType>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }

    override fun queryResultsChanged() {
        invalidate()
    }
}
