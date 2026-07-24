package eu.kanade.tachiyomi.ui.reader.viewer

import androidx.compose.ui.graphics.Color
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReaderTransitionAnimationsTest {

    @Test
    fun `parseCurve reads four control points`() {
        ReaderTransitionAnimations.parseCurve("0.66,0,0.34,1").toList() shouldBe
            listOf(0.66f, 0f, 0.34f, 1f)
    }

    @Test
    fun `parseCurve trims whitespace around values`() {
        ReaderTransitionAnimations.parseCurve(" 0.25 , 0.1 , 0.25 , 1 ").toList() shouldBe
            listOf(0.25f, 0.1f, 0.25f, 1f)
    }

    @Test
    fun `parseCurve clamps x coordinates to 0 to 1 but leaves y free`() {
        // PathInterpolator requires x in [0, 1]; y is left untouched so overshoot curves survive.
        ReaderTransitionAnimations.parseCurve("-0.5,2,1.5,-1").toList() shouldBe
            listOf(0f, 2f, 1f, -1f)
    }

    @Test
    fun `parseCurve falls back to the smooth curve when malformed`() {
        val smooth = ReaderTransitionAnimations.SMOOTH_CURVE.toList()
        ReaderTransitionAnimations.parseCurve("not-a-curve").toList() shouldBe smooth
        ReaderTransitionAnimations.parseCurve("1,2,3").toList() shouldBe smooth // too few
        ReaderTransitionAnimations.parseCurve("1,2,3,4,5").toList() shouldBe smooth // too many
        ReaderTransitionAnimations.parseCurve("").toList() shouldBe smooth
    }

    @Test
    fun `formatCurve rounds to three decimals`() {
        ReaderTransitionAnimations.formatCurve(floatArrayOf(0.6666f, 0f, 0.3334f, 1f)) shouldBe
            "0.667,0.0,0.333,1.0"
    }

    @Test
    fun `formatCurve then parseCurve round-trips an in-range curve`() {
        val curve = floatArrayOf(0.25f, 0.1f, 0.25f, 1f)
        val restored = ReaderTransitionAnimations.parseCurve(ReaderTransitionAnimations.formatCurve(curve))
        restored.toList() shouldBe curve.toList()
    }

    @Test
    fun `resolve keeps the native transition for DEFAULT`() {
        val resolved = ReaderTransitionAnimations.resolve(
            animation = ReaderPreferences.ReaderTransitionAnimation.DEFAULT,
            smoothDurationMs = 500,
            gentleDurationMs = 1000,
            customDurationMs = 1000,
            customCurve = "0.66,0,0.34,1",
        )
        resolved.interpolator shouldBe null
        resolved.durationMs shouldBe 0
    }

    @Test
    fun `contrastRatio is symmetric and hits the extremes`() {
        // Identical colors → 1:1; black vs white → the maximum 21:1 (either order).
        ReaderTransitionAnimations.contrastRatio(Color.Gray, Color.Gray) shouldBe (1f plusOrMinus 0.001f)
        ReaderTransitionAnimations.contrastRatio(Color.Black, Color.White) shouldBe (21f plusOrMinus 0.01f)
        ReaderTransitionAnimations.contrastRatio(Color.White, Color.Black) shouldBe (21f plusOrMinus 0.01f)
    }

    @Test
    fun `pickAccentWithContrast keeps the first candidate that reads against the canvas`() {
        // tertiary matches the light canvas (Lavender/Yin & Yang case) → skip to the readable secondary.
        val tertiary = Color(0xFFEDE2FF)
        val secondary = Color(0xFF7B46AF)
        val primary = Color(0xFF6D41C8)
        val canvas = Color(0xFFEDE2FF)

        ReaderTransitionAnimations.contrastRatio(tertiary, canvas) shouldBeLessThan
            ReaderTransitionAnimations.MIN_ACCENT_CONTRAST
        ReaderTransitionAnimations.contrastRatio(secondary, canvas) shouldBeGreaterThan
            ReaderTransitionAnimations.MIN_ACCENT_CONTRAST

        ReaderTransitionAnimations.pickAccentWithContrast(listOf(tertiary, secondary, primary), canvas) shouldBe
            secondary
    }

    @Test
    fun `pickAccentWithContrast prefers a distinct tertiary when it has contrast`() {
        // A vibrant tertiary on a dark canvas reads fine → used as-is for the two-tone look.
        val tertiary = Color(0xFFFFB3AC)
        val secondary = Color(0xFF7ADB8F)
        val primary = Color(0xFF7ADB8F)
        val canvas = Color(0xFF001909)

        ReaderTransitionAnimations.pickAccentWithContrast(listOf(tertiary, secondary, primary), canvas) shouldBe
            tertiary
    }

    @Test
    fun `pickAccentWithContrast falls back to the last candidate when none read`() {
        // Degenerate theme where every accent matches the canvas: still returns something to draw with.
        val canvas = Color(0xFF808080)
        val candidates = listOf(canvas, canvas, canvas)

        ReaderTransitionAnimations.pickAccentWithContrast(candidates, canvas) shouldBe candidates.last()
    }
}
