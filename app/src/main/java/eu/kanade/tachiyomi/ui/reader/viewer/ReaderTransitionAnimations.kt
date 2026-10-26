package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.ReaderTransitionAnimation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.preference.Preference
import kotlin.math.roundToInt

@ConsistentCopyVisibility
data class CubicBezier private constructor(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    fun with(x1: Float = this.x1, y1: Float = this.y1, x2: Float = this.x2, y2: Float = this.y2) = of(x1, y1, x2, y2)

    fun format(): String = listOf(x1, y1, x2, y2).joinToString(",") { ((it * 1000).roundToInt() / 1000f).toString() }

    companion object {
        val SMOOTH = of(0.25f, 0.1f, 0.25f, 1f)
        val GENTLE = of(0.586f, 0.085f, 0.342f, 0.893f)
        val EASE_OUT = of(0.33f, 1f, 0.68f, 1f)
        val EASE_IN_OUT = of(0.66f, 0f, 0.34f, 1f)
        val EASE_IN = of(0.32f, 0f, 0.67f, 0f)

        fun of(x1: Float, y1: Float, x2: Float, y2: Float) =
            CubicBezier(x1.coerceIn(0f, 1f), y1.coerceIn(0f, 1f), x2.coerceIn(0f, 1f), y2.coerceIn(0f, 1f))

        fun parse(value: String): CubicBezier? {
            val parts = value.split(',').map { it.toFloatOrNull()?.takeIf { v -> v.isFinite() } ?: return null }
            return if (parts.size == 4) of(parts[0], parts[1], parts[2], parts[3]) else null
        }
    }
}

data class PageTransition(val curve: CubicBezier, val durationMs: Int)

fun CubicBezier.toInterpolator(): Interpolator = PathInterpolator(x1, y1, x2, y2)

object ReaderTransitionAnimations {
    val SMOOTH = PageTransition(CubicBezier.SMOOTH, 500)
    val GENTLE = PageTransition(CubicBezier.GENTLE, 1000)

    const val CUSTOM_DURATION_MIN = 1
    const val CUSTOM_DURATION_MAX = 10000

    fun resolve(
        animation: ReaderTransitionAnimation,
        customCurve: CubicBezier,
        customDurationMs: Int,
    ): PageTransition? = when (animation) {
        ReaderTransitionAnimation.DEFAULT -> null
        ReaderTransitionAnimation.SMOOTH -> SMOOTH
        ReaderTransitionAnimation.GENTLE -> GENTLE
        ReaderTransitionAnimation.CUSTOM ->
            PageTransition(customCurve, customDurationMs.coerceIn(CUSTOM_DURATION_MIN, CUSTOM_DURATION_MAX))
    }

    fun transitionChanges(
        enabled: Preference<Boolean>,
        animation: Preference<ReaderTransitionAnimation>,
        customCurve: Preference<CubicBezier>,
        customDurationMs: Preference<Int>,
    ): Flow<PageTransition?> = combine(
        enabled.changes(),
        animation.changes(),
        customCurve.changes(),
        customDurationMs.changes(),
    ) { on, a, c, d ->
        if (on) resolve(a, c, d) else null
    }
}
