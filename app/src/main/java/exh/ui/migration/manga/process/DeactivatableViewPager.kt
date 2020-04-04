package exh.ui.migration.manga.process

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

class DeactivatableViewPager : androidx.viewpager.widget.ViewPager {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return !isEnabled || super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return isEnabled && super.onInterceptTouchEvent(event)
    }
}
