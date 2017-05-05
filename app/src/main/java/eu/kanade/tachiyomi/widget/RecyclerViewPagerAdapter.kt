package eu.kanade.tachiyomi.widget

import android.support.v4.view.PagerAdapter
import android.view.View
import android.view.ViewGroup
import java.util.*

abstract class RecyclerViewPagerAdapter : PagerAdapter() {

    private val pool = Stack<View>()

    var recycle = true
        set(value) {
            if (!value) pool.clear()
            field = value
        }

    protected abstract fun createView(container: ViewGroup): View

    protected abstract fun bindView(view: View, position: Int)

    protected open fun recycleView(view: View, position: Int) {}

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = if (pool.isNotEmpty()) pool.pop() else createView(container)
        bindView(view, position)
        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val view = obj as View
        recycleView(view, position)
        container.removeView(view)
        if (recycle) pool.push(view)
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view === obj
    }

}