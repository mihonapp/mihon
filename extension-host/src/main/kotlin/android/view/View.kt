package android.view

import android.content.Context
import android.os.Handler

open class View(val context: Context) {
    private val handler = Handler()
    open fun post(action: Runnable): Boolean = handler.post(action)
    open fun postDelayed(action: Runnable, delayMillis: Long): Boolean = handler.postDelayed(action, delayMillis)
    open fun removeCallbacks(action: Runnable): Boolean {
        handler.removeCallbacks(action)
        return true
    }
    var visibility: Int = VISIBLE
    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}
open class ViewGroup(context: Context) : View(context) {
    class LayoutParams(@JvmField var width: Int, @JvmField var height: Int) {
        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
    open fun removeAllViews() { }
}
