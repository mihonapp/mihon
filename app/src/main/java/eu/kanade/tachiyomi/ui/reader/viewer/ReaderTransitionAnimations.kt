package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.ReaderTransitionAnimation
import kotlin.math.max
import kotlin.math.min

/**
 * Resolves a [ReaderTransitionAnimation] into the concrete [Interpolator] and duration each viewer
 * applies. Every animation is a cubic Bézier curve plus a duration: the presets use fixed curves
 * (with user-tunable durations), [ReaderTransitionAnimation.CUSTOM] uses a user-defined curve.
 *
 * [ResolvedTransition.interpolator] is `null` for [ReaderTransitionAnimation.DEFAULT], signalling the
 * viewer to keep its native transition (ViewPager settle / RecyclerView smooth scroll).
 */
object ReaderTransitionAnimations {

    // Ease-out — responsive start, soft landing.
    val SMOOTH_CURVE = floatArrayOf(0.25f, 0.1f, 0.25f, 1f)

    // Ease-in-out — soft start and stop.
    val GENTLE_CURVE = floatArrayOf(0.586f, 0.085f, 0.342f, 0.893f)

    fun resolve(
        animation: ReaderTransitionAnimation,
        smoothDurationMs: Int,
        gentleDurationMs: Int,
        customDurationMs: Int,
        customCurve: String,
    ): ResolvedTransition = when (animation) {
        ReaderTransitionAnimation.DEFAULT -> ResolvedTransition(null, 0)
        ReaderTransitionAnimation.SMOOTH -> ResolvedTransition(interpolatorOf(SMOOTH_CURVE), smoothDurationMs)
        ReaderTransitionAnimation.GENTLE -> ResolvedTransition(interpolatorOf(GENTLE_CURVE), gentleDurationMs)
        ReaderTransitionAnimation.CUSTOM -> ResolvedTransition(interpolatorOf(parseCurve(customCurve)), customDurationMs)
    }

    fun interpolatorOf(curve: FloatArray): Interpolator =
        PathInterpolator(curve[0].coerceIn(0f, 1f), curve[1], curve[2].coerceIn(0f, 1f), curve[3])

    /**
     * Parses a "x1,y1,x2,y2" string into Bézier control points, falling back to [SMOOTH_CURVE] when
     * malformed. The X coordinates are clamped to [0, 1] (required by [PathInterpolator]).
     */
    fun parseCurve(value: String): FloatArray {
        val parts = value.split(',').mapNotNull { it.trim().toFloatOrNull() }
        if (parts.size != 4) return SMOOTH_CURVE
        return floatArrayOf(parts[0].coerceIn(0f, 1f), parts[1], parts[2].coerceIn(0f, 1f), parts[3])
    }

    fun formatCurve(curve: FloatArray): String =
        curve.joinToString(",") { (Math.round(it * 1000f) / 1000f).toString() }

    /**
     * Minimum WCAG luminance contrast ratio an accent must clear against the curve-editor canvas to be
     * considered legible; below this it's treated as blending into the background.
     */
    const val MIN_ACCENT_CONTRAST = 2f

    /**
     * Picks the far-end accent for the curve editor. Tries [candidates] in order (typically tertiary,
     * then secondary, then primary) and returns the first that clears [MIN_ACCENT_CONTRAST] against
     * [canvas]; falls back to the last candidate when none do. This lets themes with a distinct
     * tertiary render a two-tone curve while themes whose tertiary matches the background (e.g.
     * Lavender, Yin & Yang) fall back to a color that still reads — with no per-theme special-casing.
     */
    fun pickAccentWithContrast(candidates: List<Color>, canvas: Color): Color =
        candidates.firstOrNull { contrastRatio(it, canvas) >= MIN_ACCENT_CONTRAST } ?: candidates.last()

    /** WCAG relative-luminance contrast ratio between two opaque colors, in the range 1..21. */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}

data class ResolvedTransition(
    val interpolator: Interpolator?,
    val durationMs: Int,
)
