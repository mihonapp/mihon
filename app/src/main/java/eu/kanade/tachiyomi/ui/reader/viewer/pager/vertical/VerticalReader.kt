package eu.kanade.tachiyomi.ui.reader.viewer.pager.vertical

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader

/**
 * Vertical reader.
 */
class VerticalReader : PagerReader() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View? {
        return VerticalPager(activity).apply { initializePager(this) }
    }

}
