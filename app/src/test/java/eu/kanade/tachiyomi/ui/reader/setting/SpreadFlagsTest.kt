package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpreadFlagsTest {

    @Test
    fun `fromPreference round-trips every entry`() {
        Spread.entries.forEach { assertEquals(it, Spread.fromPreference(it.flagValue)) }
        SpreadForcePairing.entries.forEach { assertEquals(it, SpreadForcePairing.fromPreference(it.flagValue)) }
        SpreadVerticalFit.entries.forEach { assertEquals(it, SpreadVerticalFit.fromPreference(it.flagValue)) }
        SpreadShift.entries.forEach { assertEquals(it, SpreadShift.fromPreference(it.flagValue)) }
        SpreadSoloPage.entries.forEach { assertEquals(it, SpreadSoloPage.fromPreference(it.flagValue)) }
        SpreadWidePairing.entries.forEach { assertEquals(it, SpreadWidePairing.fromPreference(it.flagValue)) }
    }

    @Test
    fun `fromPreference defaults on null or an unrecognized value`() {
        assertEquals(Spread.DEFAULT, Spread.fromPreference(null))
        assertEquals(Spread.DEFAULT, Spread.fromPreference(0x7FFFFFFF))
        assertEquals(SpreadForcePairing.DEFAULT, SpreadForcePairing.fromPreference(null))
        assertEquals(SpreadVerticalFit.DEFAULT, SpreadVerticalFit.fromPreference(-1))
        assertEquals(SpreadVerticalFit.DEFAULT, SpreadVerticalFit.fromPreference(0x1))
    }

    @Test
    fun `each flag value fits within its own mask`() {
        Spread.entries.forEach { assertEquals(it.flagValue, it.flagValue and Spread.MASK) }
        SpreadForcePairing.entries.forEach { assertEquals(it.flagValue, it.flagValue and SpreadForcePairing.MASK) }
        SpreadVerticalFit.entries.forEach { assertEquals(it.flagValue, it.flagValue and SpreadVerticalFit.MASK) }
        SpreadShift.entries.forEach { assertEquals(it.flagValue, it.flagValue and SpreadShift.MASK) }
        SpreadSoloPage.entries.forEach { assertEquals(it.flagValue, it.flagValue and SpreadSoloPage.MASK) }
        SpreadWidePairing.entries.forEach { assertEquals(it.flagValue, it.flagValue and SpreadWidePairing.MASK) }
    }

    @Test
    fun `masks occupy distinct bits and don't collide with existing viewer flags`() {
        val masks = listOf(
            "Spread" to Spread.MASK,
            "Force" to SpreadForcePairing.MASK,
            "VerticalFit" to SpreadVerticalFit.MASK,
            "Shift" to SpreadShift.MASK,
            "SoloPage" to SpreadSoloPage.MASK,
            "WidePairing" to SpreadWidePairing.MASK,
            "ReadingMode" to ReadingMode.MASK,
            "Orientation" to ReaderOrientation.MASK,
        )
        for (i in masks.indices) {
            for (j in i + 1 until masks.size) {
                assertEquals(
                    0,
                    masks[i].second and masks[j].second,
                    "${masks[i].first} and ${masks[j].first} masks overlap",
                )
            }
        }
    }

    @Test
    fun `several flags coexist in one viewerFlags value without interfering`() {
        // Pack one non-default from each into a single value and confirm each reads back
        // independently, exactly what the Manga.spread* extensions do (viewerFlags and MASK).
        val flags = Spread.ENABLED.flagValue or
            SpreadForcePairing.DISABLED.flagValue or
            SpreadVerticalFit.TOP.flagValue or
            SpreadShift.SHIFTED.flagValue or
            SpreadSoloPage.JUSTIFY.flagValue or
            SpreadWidePairing.ENABLED.flagValue

        assertEquals(Spread.ENABLED, Spread.fromPreference(flags and Spread.MASK))
        assertEquals(SpreadForcePairing.DISABLED, SpreadForcePairing.fromPreference(flags and SpreadForcePairing.MASK))
        assertEquals(SpreadVerticalFit.TOP, SpreadVerticalFit.fromPreference(flags and SpreadVerticalFit.MASK))
        assertEquals(SpreadShift.SHIFTED, SpreadShift.fromPreference(flags and SpreadShift.MASK))
        assertEquals(SpreadSoloPage.JUSTIFY, SpreadSoloPage.fromPreference(flags and SpreadSoloPage.MASK))
        assertEquals(SpreadWidePairing.ENABLED, SpreadWidePairing.fromPreference(flags and SpreadWidePairing.MASK))
    }
}
