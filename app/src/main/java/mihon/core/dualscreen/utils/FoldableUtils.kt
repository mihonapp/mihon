package mihon.core.dualscreen.utils

import android.app.Activity
import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Utility class to handle foldable device information.
 */
object FoldableUtils {

    /**
     * Returns a flow of WindowLayoutInfo for the given activity.
     */
    fun windowLayoutInfoFlow(activity: Activity): Flow<WindowLayoutInfo> {
        return WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }

    /**
     * Returns the folding feature if present and active.
     */
    fun getFoldingFeature(info: WindowLayoutInfo): FoldingFeature? {
        return info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
    }

    /**
     * Returns the bounds of the hinge/fold if it's splitting the layout.
     */
    fun getHingeBounds(info: WindowLayoutInfo): Rect? {
        val feature = getFoldingFeature(info) ?: return null
        // We only care about hinges that split the screen (Surface Duo style) 
        // or folds that are half-opened.
        return if (feature.state == FoldingFeature.State.HALF_OPENED || feature.isSeparating) {
            feature.bounds
        } else {
            null
        }
    }

    /**
     * Returns true if the device is currently spanned across a hinge or half-folded.
     */
    fun isSpanned(info: WindowLayoutInfo): Boolean {
        val feature = getFoldingFeature(info) ?: return false
        return feature.isSeparating || feature.state == FoldingFeature.State.HALF_OPENED
    }
}
