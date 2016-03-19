package eu.kanade.tachiyomi.util

import android.graphics.Point
import android.view.View

/**
 * Returns coordinates of view.
 * Used for animation
 *
 * @return coordinates of view
 */
fun View.getCoordinates(): Point
{
    var cx = (this.left + this.right) / 2;
    var cy = (this.top + this.bottom) / 2;

    return Point(cx, cy)
}

