package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReaderTransitionAnimationsTest {

    @Test
    fun `parse reads four control points`() {
        CubicBezier.parse("0.66,0,0.34,1") shouldBe CubicBezier.of(0.66f, 0f, 0.34f, 1f)
    }

    @Test
    fun `parse clamps x and y to 0 to 1`() {
        CubicBezier.parse("-0.5,2,1.5,-1") shouldBe CubicBezier.of(0f, 1f, 1f, 0f)
        CubicBezier.parse("1.5,-1,-0.5,2") shouldBe CubicBezier.of(1f, 0f, 0f, 1f)
    }

    @Test
    fun `parse rejects malformed input`() {
        CubicBezier.parse("a,b,c,d") shouldBe null
        CubicBezier.parse("1,x,2,3,4") shouldBe null
        CubicBezier.parse("0.1,0.2,0.3") shouldBe null
        CubicBezier.parse("NaN,0,0,1") shouldBe null
        CubicBezier.parse("0,Infinity,0,1") shouldBe null
    }

    @Test
    fun `format then parse round-trips`() {
        val curve = CubicBezier.of(0.586f, 0.085f, 0.342f, 0.893f)
        CubicBezier.parse(curve.format()) shouldBe curve
    }

    @Test
    fun `format writes the stored three-decimal form`() {
        CubicBezier.EASE_IN_OUT.format() shouldBe "0.66,0.0,0.34,1.0"
        CubicBezier.of(0.1234f, 0f, 1f, 0.5f).format() shouldBe "0.123,0.0,1.0,0.5"
    }

    @Test
    fun `resolve maps each option`() {
        val custom = CubicBezier.of(0.1f, 0.2f, 0.3f, 0.4f)
        val ms = 1234
        val expected = mapOf(
            ReaderPreferences.ReaderTransitionAnimation.DEFAULT to null,
            ReaderPreferences.ReaderTransitionAnimation.SMOOTH to PageTransition(CubicBezier.SMOOTH, 500),
            ReaderPreferences.ReaderTransitionAnimation.GENTLE to PageTransition(CubicBezier.GENTLE, 1000),
            ReaderPreferences.ReaderTransitionAnimation.CUSTOM to PageTransition(custom, ms),
        )

        expected.keys shouldBe ReaderPreferences.ReaderTransitionAnimation.entries.toSet()
        expected.forEach { (animation, transition) ->
            ReaderTransitionAnimations.resolve(animation, custom, ms) shouldBe transition
        }

        val customOption = ReaderPreferences.ReaderTransitionAnimation.CUSTOM
        ReaderTransitionAnimations.resolve(customOption, custom, 0) shouldBe PageTransition(custom, 1)
        ReaderTransitionAnimations.resolve(customOption, custom, 20000) shouldBe PageTransition(custom, 10000)
    }
}
