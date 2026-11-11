package eu.kanade.tachiyomi.ui.reader.viewer.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The quintic ease out that RecyclerView and ViewPager move with.
 */
val QuinticOutEasing = Easing { t ->
    val x = t - 1f
    x * x * x * x * x + 1f
}

/**
 * ViewPager's settle onto a page [distance] away: from the release [velocity] when there is one, else 100ms plus
 * 100ms per page, at most 600ms.
 */
fun pageSettleSpec(distance: Float, pageSize: Int, velocity: Float): AnimationSpec<Float> {
    val size = pageSize.coerceAtLeast(1)
    val duration = if (abs(velocity) > 0f) {
        val half = size / 2f
        val ratio = min(1f, abs(distance) / size)
        // Distance from the edge counts less the closer it is, as ViewPager's distanceInfluenceForSnapDuration
        val influenced = half + half * sin((ratio - 0.5f) * 0.3f * PI.toFloat() / 2f)
        4 * (1000 * abs(influenced / velocity)).roundToInt()
    } else {
        ((abs(distance) / size + 1) * 100).toInt()
    }
    return tween(min(duration, 600), easing = QuinticOutEasing)
}
