package eu.kanade.tachiyomi.ui.catalogue

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View

class SourceDividerItemDecoration(context: Context) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {

    private val divider: Drawable

    init {
        val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.listDivider))
        divider = a.getDrawable(0)!!
        a.recycle()
    }

    override fun onDraw(c: Canvas, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val holder = parent.getChildViewHolder(child)
            if (holder is SourceHolder &&
                    parent.getChildViewHolder(parent.getChildAt(i + 1)) is SourceHolder) {
                val params = child.layoutParams as androidx.recyclerview.widget.RecyclerView.LayoutParams
                val top = child.bottom + params.bottomMargin
                val bottom = top + divider.intrinsicHeight
                val left = parent.paddingLeft + holder.margin
                val right = parent.width - parent.paddingRight - holder.margin

                divider.setBounds(left, top, right, bottom)
                divider.draw(c)
            }
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView,
                                state: androidx.recyclerview.widget.RecyclerView.State) {
        outRect.set(0, 0, 0, divider.intrinsicHeight)
    }

}
