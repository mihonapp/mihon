package eu.kanade.tachiyomi.util

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat

/**
 * Set a vector on a [ImageView].
 *
 * @param drawable id of drawable resource
 */
fun ImageView.setVectorCompat(@DrawableRes drawable: Int, tint: Int? = null) {
    val vector = VectorDrawableCompat.create(resources, drawable, context.theme)
    if (tint != null) {
        vector?.mutate()
        vector?.setTint(tint)
    }
    setImageDrawable(vector)
}
