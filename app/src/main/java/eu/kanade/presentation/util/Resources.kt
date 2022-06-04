package eu.kanade.presentation.util

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Load a quantity string resource.
 *
 * @param id the resource identifier
 * @param quantity The number used to get the string for the current language's plural rules.
 * @return the string data associated with the resource
 */
@Composable
@ReadOnlyComposable
fun quantityStringResource(@PluralsRes id: Int, quantity: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(id, quantity, quantity)
}

/**
 * Load a quantity string resource with formatting.
 *
 * @param id the resource identifier
 * @param quantity The number used to get the string for the current language's plural rules.
 * @param formatArgs the format arguments
 * @return the string data associated with the resource
 */
@Composable
@ReadOnlyComposable
fun quantityStringResource(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(id, quantity, *formatArgs)
}

/**
 * Create a BitmapPainter from an drawable resource.
 *
 * > Only use this if [androidx.compose.ui.res.painterResource] doesn't work.
 *
 * @param id the resource identifier
 * @return the bitmap associated with the resource
 */
@Composable
fun rememberResourceBitmapPainter(@DrawableRes id: Int): BitmapPainter {
    val context = LocalContext.current
    return remember(id) {
        val drawable = ContextCompat.getDrawable(context, id)
            ?: throw Resources.NotFoundException()
        BitmapPainter(drawable.toBitmap().asImageBitmap())
    }
}
