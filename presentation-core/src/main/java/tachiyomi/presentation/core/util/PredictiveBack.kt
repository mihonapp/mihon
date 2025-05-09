package tachiyomi.presentation.core.util

import androidx.compose.animation.core.CubicBezierEasing

private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

object PredictiveBack {
    fun transform(progress: Float): Float = PredictiveBackEasing.transform(progress)
}
