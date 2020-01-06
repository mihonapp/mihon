package eu.kanade.tachiyomi.widget

import android.content.Context

class PreCachingLayoutManager(context: Context) : androidx.recyclerview.widget.LinearLayoutManager(context) {

    init {
        isItemPrefetchEnabled = false
    }

    companion object {
        const val DEFAULT_EXTRA_LAYOUT_SPACE = 600
    }

    var extraLayoutSpace = 0

    override fun getExtraLayoutSpace(state: androidx.recyclerview.widget.RecyclerView.State): Int {
        if (extraLayoutSpace > 0) {
            return extraLayoutSpace
        }
        return DEFAULT_EXTRA_LAYOUT_SPACE
    }

}
