package app.komikita.core.nativecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeChapterEngineTest {
    @Test
    fun countsGapsThroughJni() {
        val cases = listOf(
            doubleArrayOf() to 0,
            doubleArrayOf(8.0, 3.2, 1.0, 0.0, 3.7, Double.NaN, -0.5, Double.POSITIVE_INFINITY) to 5,
            doubleArrayOf(Int.MAX_VALUE.toDouble()) to Int.MAX_VALUE - 1,
            doubleArrayOf(2.0, Int.MAX_VALUE + 0.5) to 1,
            DoubleArray(1_024) { (1_024 - it) * 2.0 } to 1_024,
        )

        cases.forEach { (numbers, expected) ->
            val actual = NativeChapterEngine.missingChaptersCount(numbers)
            assertEquals("JNI result for ${numbers.size} chapter numbers", expected, actual)
        }
    }

    @Test
    fun rejectsOversizedBatches() {
        assertEquals(0, NativeChapterEngine.missingChaptersCount(doubleArrayOf(1.0)))
        assertNull(NativeChapterEngine.missingChaptersCount(DoubleArray(1_000_001)))

        val oversizedList = object : AbstractList<Double>() {
            override val size = 1_000_001

            override fun get(index: Int): Double = error("Oversized input should not be read")
        }
        assertNull(NativeChapterEngine.missingChaptersCount(oversizedList))
    }
}
