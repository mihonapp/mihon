package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NightFilterWindowTest {

    @Test
    fun `day window inside is active`() {
        assertTrue(ReaderPreferences.isInNightWindow(600, 360, 1080))
    }

    @Test
    fun `day window outside is inactive`() {
        assertFalse(ReaderPreferences.isInNightWindow(200, 360, 1080))
        assertFalse(ReaderPreferences.isInNightWindow(1200, 360, 1080))
    }

    @Test
    fun `day window start inclusive end exclusive`() {
        assertTrue(ReaderPreferences.isInNightWindow(360, 360, 1080))
        assertFalse(ReaderPreferences.isInNightWindow(1080, 360, 1080))
    }

    @Test
    fun `overnight window evening side active`() {
        assertTrue(ReaderPreferences.isInNightWindow(1380, 1320, 360))
    }

    @Test
    fun `overnight window morning side active`() {
        assertTrue(ReaderPreferences.isInNightWindow(120, 1320, 360))
    }

    @Test
    fun `overnight window midday inactive`() {
        assertFalse(ReaderPreferences.isInNightWindow(720, 1320, 360))
    }

    @Test
    fun `overnight boundaries start inclusive end exclusive`() {
        assertTrue(ReaderPreferences.isInNightWindow(1320, 1320, 360))
        assertFalse(ReaderPreferences.isInNightWindow(360, 1320, 360))
    }

    @Test
    fun `midnight belongs to overnight window`() {
        assertTrue(ReaderPreferences.isInNightWindow(0, 1320, 360))
        assertFalse(ReaderPreferences.isInNightWindow(0, 360, 1080))
    }

    @Test
    fun `start equals end means disabled`() {
        assertFalse(ReaderPreferences.isInNightWindow(500, 600, 600))
        assertFalse(ReaderPreferences.isInNightWindow(600, 600, 600))
    }

    @Test
    fun `out of range inputs are normalized`() {
        assertFalse(ReaderPreferences.isInNightWindow(1500, 360, 1080))
        assertTrue(ReaderPreferences.isInNightWindow(1500, 1320, 360))
        assertTrue(ReaderPreferences.isInNightWindow(1500, 0, 120))
        assertTrue(ReaderPreferences.isInNightWindow(-12, 1320, 360))
    }

    @Test
    fun `almost full day overnight leaves single minute off`() {
        assertFalse(ReaderPreferences.isInNightWindow(0, 1, 0))
        assertTrue(ReaderPreferences.isInNightWindow(1, 1, 0))
        assertTrue(ReaderPreferences.isInNightWindow(720, 1, 0))
    }

    @Test
    fun `almost full day daytime leaves single minute on`() {
        assertTrue(ReaderPreferences.isInNightWindow(0, 0, 1))
        assertFalse(ReaderPreferences.isInNightWindow(1, 0, 1))
        assertFalse(ReaderPreferences.isInNightWindow(720, 0, 1))
    }

    @Test
    fun `one minute window`() {
        assertTrue(ReaderPreferences.isInNightWindow(100, 100, 101))
        assertFalse(ReaderPreferences.isInNightWindow(101, 100, 101))
        assertTrue(ReaderPreferences.isInNightWindow(1439, 1439, 0))
        assertFalse(ReaderPreferences.isInNightWindow(0, 1439, 0))
    }

    @Test
    fun `or logic manual plus schedule combinations`() {
        fun effective(manual: Boolean, schedule: Boolean, inWindow: Boolean) = manual || (schedule && inWindow)
        assertFalse(effective(false, false, false))
        assertFalse(effective(false, false, true))
        assertFalse(effective(false, true, false))
        assertTrue(effective(false, true, true))
        assertTrue(effective(true, false, false))
        assertTrue(effective(true, false, true))
        assertTrue(effective(true, true, false))
        assertTrue(effective(true, true, true))
    }

    @Test
    fun `defaults 22 to 6 cover night`() {
        assertTrue(ReaderPreferences.isInNightWindow(23 * 60, 22 * 60, 6 * 60))
        assertTrue(ReaderPreferences.isInNightWindow(3 * 60, 22 * 60, 6 * 60))
        assertFalse(ReaderPreferences.isInNightWindow(12 * 60, 22 * 60, 6 * 60))
        assertFalse(ReaderPreferences.isInNightWindow(12 * 60, 12 * 60, 12 * 60))
    }
}
