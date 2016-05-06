package eu.kanade.tachiyomi.widget

import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView

class EndlessScrollListener(
        private val layoutManager: LinearLayoutManager,
        private val requestNext: () -> Unit)
: RecyclerView.OnScrollListener() {

    companion object {
        // The minimum amount of items to have below your current scroll position before loading
        // more.
        private val VISIBLE_THRESHOLD = 5
    }

    private var previousTotal = 0 // The total number of items in the dataset after the last load
    private var loading = true // True if we are still waiting for the last set of data to load.
    private var firstVisibleItem = 0
    private var visibleItemCount = 0
    private var totalItemCount = 0

    fun resetScroll() {
        previousTotal = 0
        loading = true
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        visibleItemCount = recyclerView.childCount
        totalItemCount = layoutManager.itemCount
        firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

        if (loading && totalItemCount > previousTotal) {
            loading = false
            previousTotal = totalItemCount
        }
        if (!loading && totalItemCount - visibleItemCount <= firstVisibleItem + VISIBLE_THRESHOLD) {
            // End has been reached
            requestNext()
            loading = true
        }
    }

}