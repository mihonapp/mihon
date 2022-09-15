package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Display a toast in this context.
 *
 * @param resource the text resource.
 * @param duration the duration of the toast. Defaults to short.
 */
fun Context.toast(@StringRes resource: Int, duration: Int = Toast.LENGTH_SHORT, block: (Toast) -> Unit = {}): Toast {
    return toast(getString(resource), duration, block)
}

/**
 * Display a toast in this context.
 *
 * @param text the text to display.
 * @param duration the duration of the toast. Defaults to short.
 */
fun Context.toast(text: String?, duration: Int = Toast.LENGTH_SHORT, block: (Toast) -> Unit = {}): Toast {
    return Toast.makeText(applicationContext, text.orEmpty(), duration).also {
        block(it)
        it.show()
    }
}
