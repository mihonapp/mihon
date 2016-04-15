package eu.kanade.tachiyomi.widget

import android.content.Context
import android.support.v7.widget.RecyclerView

class PreCachingLayoutManager(context: Context) : NpaLinearLayoutManager(context) {

    companion object {
        const val DEFAULT_EXTRA_LAYOUT_SPACE = 600
    }

    var extraLayoutSpace = 0

    override fun getExtraLayoutSpace(state: RecyclerView.State): Int {
        if (extraLayoutSpace > 0) {
            return extraLayoutSpace
        }
        return DEFAULT_EXTRA_LAYOUT_SPACE
    }

}
