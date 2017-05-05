package eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader

/**
 * Left to Right reader.
 */
class LeftToRightReader : PagerReader() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View? {
        return HorizontalPager(activity).apply { initializePager(this) }
    }

}
