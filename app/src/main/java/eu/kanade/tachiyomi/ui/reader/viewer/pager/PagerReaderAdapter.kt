package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.PagerAdapter
import android.view.ViewGroup

import eu.kanade.tachiyomi.data.source.model.Page

/**
 * Adapter of pages for a ViewPager.
 *
 * @param fm the fragment manager.
 */
class PagerReaderAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {

    /**
     * Pages stored in the adapter.
     */
    var pages: List<Page>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /**
     * Returns the number of pages.
     *
     * @return the number of pages or 0 if the list is null.
     */
    override fun getCount(): Int {
        return pages?.size ?: 0
    }

    /**
     * Creates a new fragment for the given position when it's called.
     *
     * @param position the position to instantiate.
     * @return a fragment for the given position.
     */
    override fun getItem(position: Int): Fragment {
        return PagerReaderFragment.newInstance()
    }

    /**
     * Instantiates a fragment in the given position.
     *
     * @param container the parent view.
     * @param position the position to instantiate.
     * @return an instance of a fragment for the given position.
     */
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val f = super.instantiateItem(container, position) as PagerReaderFragment
        f.page = pages!![position]
        f.position = position
        return f
    }

    /**
     * Returns the position of a given item.
     *
     * @param obj the item to find its position.
     * @return the position for the item.
     */
    override fun getItemPosition(obj: Any): Int {
        val f = obj as PagerReaderFragment
        val position = f.position
        if (position >= 0 && position < count) {
            if (pages!![position] === f.page) {
                return PagerAdapter.POSITION_UNCHANGED
            } else {
                return PagerAdapter.POSITION_NONE
            }
        }
        return super.getItemPosition(obj)
    }
}
