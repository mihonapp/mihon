package eu.kanade.tachiyomi.widget

import com.google.android.material.appbar.AppBarLayout

/**
 * [AppBarLayout.ScrollingViewBehavior] that lets the app bar overlaps the scrolling child.
 */
class TachiyomiScrollingViewBehavior : AppBarLayout.ScrollingViewBehavior() {

    var shouldHeaderOverlap = false

    override fun shouldHeaderOverlapScrollingChild(): Boolean {
        return shouldHeaderOverlap
    }
}
