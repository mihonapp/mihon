package eu.kanade.tachiyomi.widget

import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.ProgressBar
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.visible

/**
 * A glide target to display an image with an optional progress bar and a configurable scale type
 * for the error drawable.
 *
 * @param view the view where the image will be loaded
 * @param progress an optional progress bar to show when the image is loading.
 * @param errorScaleType the scale type for the error drawable, [ScaleType.CENTER] by default.
 */
class StateImageViewTarget(view: ImageView,
                           val progress: ProgressBar? = null,
                           val errorScaleType: ScaleType = ScaleType.CENTER) :
        GlideDrawableImageViewTarget(view) {

    private val imageScaleType = view.scaleType

    override fun onLoadStarted(placeholder: Drawable?) {
        progress?.visible()
        super.onLoadStarted(placeholder)
    }

    override fun onLoadFailed(e: Exception?, errorDrawable: Drawable?) {
        progress?.gone()
        view.scaleType = errorScaleType
        super.onLoadFailed(e, errorDrawable)
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        progress?.gone()
        super.onLoadCleared(placeholder)
    }

    override fun onResourceReady(resource: GlideDrawable?, animation: GlideAnimation<in GlideDrawable>?) {
        progress?.gone()
        view.scaleType = imageScaleType
        super.onResourceReady(resource, animation)
    }
}