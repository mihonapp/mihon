package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.widget.Toast
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.i18n.stringResource

/**
 * Display a toast in this context.
 *
 * @param resource the text resource.
 * @param duration the duration of the toast. Defaults to short.
 */
fun Context.toast(
    resource: StringResource,
    duration: Int = Toast.LENGTH_SHORT,
    block: (Toast) -> Unit = {},
): Toast {
    return toast(stringResource(resource), duration, block)
}

/**
 * Display a toast in this context.
 *
 * @param text the text to display.
 * @param duration the duration of the toast. Defaults to short.
 */
fun Context.toast(
    text: String?,
    duration: Int = Toast.LENGTH_SHORT,
    block: (Toast) -> Unit = {},
): Toast {
    return Toast.makeText(applicationContext, text.orEmpty(), duration).also {
        block(it)
        it.show()
    }
}
