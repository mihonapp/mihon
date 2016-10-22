package eu.kanade.tachiyomi.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View

class DividerItemDecoration : RecyclerView.ItemDecoration {

    private val divider: Drawable?

    constructor(context: Context, attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.listDivider))
        divider = a.getDrawable(0)
        a.recycle()
    }

    constructor(divider: Drawable) {
        this.divider = divider
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
        super.getItemOffsets(outRect, view, parent, state)
        if (divider == null) return
        if (parent.getChildPosition(view) < 1) return

        if (getOrientation(parent) == LinearLayoutManager.VERTICAL)
            outRect.top = divider.intrinsicHeight
        else
            outRect.left = divider.intrinsicWidth
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State?) {
        if (divider == null) {
            super.onDrawOver(c, parent, state)
            return
        }

        if (getOrientation(parent) == LinearLayoutManager.VERTICAL) {
            val left = parent.paddingLeft
            val right = parent.width - parent.paddingRight
            val childCount = parent.childCount
            val dividerHeight = divider.intrinsicHeight

            for (i in 1..childCount - 1) {
                val child = parent.getChildAt(i)
                val params = child.layoutParams as RecyclerView.LayoutParams
                val ty = (child.translationY + 0.5f).toInt()
                val top = child.top - params.topMargin + ty
                val bottom = top + dividerHeight
                divider.setBounds(left, top, right, bottom)
                divider.draw(c)
            }
        } else { //horizontal
            val top = parent.paddingTop
            val bottom = parent.height - parent.paddingBottom
            val childCount = parent.childCount

            for (i in 1..childCount - 1) {
                val child = parent.getChildAt(i)
                val params = child.layoutParams as RecyclerView.LayoutParams
                val size = divider.intrinsicWidth
                val left = child.left - params.leftMargin
                val right = left + size
                divider.setBounds(left, top, right, bottom)
                divider.draw(c)
            }
        }
    }

    private fun getOrientation(parent: RecyclerView): Int {
        if (parent.layoutManager is LinearLayoutManager) {
            val layoutManager = parent.layoutManager as LinearLayoutManager
            return layoutManager.orientation
        } else
            throw IllegalStateException("DividerItemDecoration can only be used with a LinearLayoutManager.")
    }

}