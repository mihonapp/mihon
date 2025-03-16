package eu.kanade.tachiyomi.util.system

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import coil3.size.ScaleDrawable

fun Drawable.getBitmapOrNull(): Bitmap? = when (this) {
    is BitmapDrawable -> bitmap
    is ScaleDrawable -> child.toBitmap()
    else -> null
}
