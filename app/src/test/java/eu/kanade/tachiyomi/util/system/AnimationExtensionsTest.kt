package eu.kanade.tachiyomi.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnimationExtensionsTest {

    @Test
    fun `keeps duration at normal scale`() {
        assertEquals(500, 500.scaledByAnimatorDuration(1f))
    }

    @Test
    fun `applies scale`() {
        assertEquals(250, 500.scaledByAnimatorDuration(0.5f))
        assertEquals(1000, 500.scaledByAnimatorDuration(2f))
    }

    @Test
    fun `returns zero when animations are off`() {
        assertEquals(0, 500.scaledByAnimatorDuration(0f))
        assertEquals(0, 500.scaledByAnimatorDuration(-1f))
    }

    @Test
    fun `keeps already instant durations instant`() {
        assertEquals(0, 0.scaledByAnimatorDuration(1f))
        assertEquals(0, 0.scaledByAnimatorDuration(0f))
    }

    @Test
    fun `never rounds a positive duration down to zero`() {
        assertEquals(1, 1.scaledByAnimatorDuration(0.2f))
        assertEquals(1, 3.scaledByAnimatorDuration(0.1f))
    }
}
