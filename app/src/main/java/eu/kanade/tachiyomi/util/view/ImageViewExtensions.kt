package eu.kanade.tachiyomi.util.view

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import coil.ImageLoader
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.target.ImageViewTarget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Set a vector on a [ImageView].
 *
 * @param drawable id of drawable resource
 */
fun ImageView.setVectorCompat(@DrawableRes drawable: Int, @AttrRes tint: Int? = null) {
    val vector = AppCompatResources.getDrawable(context, drawable)
    if (tint != null) {
        vector?.mutate()
        vector?.setTint(context.getResourceColor(tint))
    }
    setImageDrawable(vector)
}

/**
 * Load the image referenced by [data] and set it on this [ImageView],
 * and if the image is animated, this will also disable that animation
 * if [Context.animatorDurationScale] is 0
 */
fun ImageView.loadAutoPause(
    data: Any?,
    loader: ImageLoader = context.imageLoader,
    builder: ImageRequest.Builder.() -> Unit = {},
) {
    load(data, loader) {
        placeholder(ColorDrawable(context.getColor(R.color.cover_placeholder)))
        error(R.drawable.cover_error)

        // Build the original request so we can add on our success listener
        val originalListener = apply(builder).build().listener
        listener(
            onSuccess = { request, metadata ->
                (request.target as? ImageViewTarget)?.drawable.let {
                    if (it is Animatable && context.animatorDurationScale == 0f) it.stop()
                }
                originalListener?.onSuccess(request, metadata)
            },
            onStart = { request -> originalListener?.onStart(request) },
            onCancel = { request -> originalListener?.onCancel(request) },
            onError = { request, throwable -> originalListener?.onError(request, throwable) },
        )
    }
}
