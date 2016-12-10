package eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader

/**
 * Right to Left reader.
 */
class RightToLeftReader : PagerReader() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View? {
        return HorizontalPager(activity).apply {
            rotation = 180f
            initializePager(this)
        }
    }

    /**
     * Moves a page to the right.
     */
    override fun moveRight() {
        moveToPrevious()
    }

    /**
     * Moves a page to the left.
     */
    override fun moveLeft() {
        moveToNext()
    }

    /**
     * Moves a page down.
     */
    override fun moveDown() {
        moveToNext()
    }

    /**
     * Moves a page up.
     */
    override fun moveUp() {
        moveToPrevious()
    }

}
