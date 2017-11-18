package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet

class PageIndicatorTextView(context: Context, attrs: AttributeSet? = null) :
        AppCompatTextView(context, attrs) {

    override fun onDraw(canvas: Canvas) {
        // We want the shadow to look like an outline
        for (i in 0..5) {
            super.onDraw(canvas)
        }
    }
}