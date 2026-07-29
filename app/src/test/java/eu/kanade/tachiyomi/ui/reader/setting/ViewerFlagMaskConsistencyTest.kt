package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reading mode, orientation and every spread setting pack into a single `Manga.viewerFlags` int as
 * disjoint bitfields (see the extension getters in `Manga.kt`, each `viewerFlags and <X>.MASK`).
 * If two masks overlapped, writing one setting would silently corrupt another, so this pins the
 * packing: masks are pairwise disjoint, and each enum's values live inside its own mask. Adding a
 * new flag whose mask collides with an existing one fails here rather than in the field.
 */
class ViewerFlagMaskConsistencyTest {

    // Every field that shares Manga.viewerFlags: name -> (mask, its flag values).
    private val fields: List<Triple<String, Int, List<Int>>> = listOf(
        Triple("ReadingMode", ReadingMode.MASK, ReadingMode.entries.map { it.flagValue }),
        Triple("ReaderOrientation", ReaderOrientation.MASK, ReaderOrientation.entries.map { it.flagValue }),
        Triple("Spread", Spread.MASK, Spread.entries.map { it.flagValue }),
        Triple("SpreadForcePairing", SpreadForcePairing.MASK, SpreadForcePairing.entries.map { it.flagValue }),
        Triple("SpreadVerticalFit", SpreadVerticalFit.MASK, SpreadVerticalFit.entries.map { it.flagValue }),
        Triple("SpreadShift", SpreadShift.MASK, SpreadShift.entries.map { it.flagValue }),
        Triple("SpreadSoloPage", SpreadSoloPage.MASK, SpreadSoloPage.entries.map { it.flagValue }),
    )

    private fun hex(v: Int) = "0x" + v.toString(16)

    @Test
    fun `masks are pairwise disjoint`() {
        for (i in fields.indices) {
            for (j in i + 1 until fields.size) {
                val (na, ma, _) = fields[i]
                val (nb, mb, _) = fields[j]
                assertEquals(
                    0,
                    ma and mb,
                    "viewerFlags masks overlap: $na (${hex(ma)}) & $nb (${hex(mb)}) = ${hex(ma and mb)}",
                )
            }
        }
    }

    @Test
    fun `every flag value stays within its own mask`() {
        fields.forEach { (name, mask, values) ->
            values.forEach { v ->
                assertEquals(v, v and mask, "$name value ${hex(v)} escapes its mask ${hex(mask)}")
            }
        }
    }

    @Test
    fun `flag values within a field are distinct`() {
        fields.forEach { (name, _, values) ->
            assertEquals(values.size, values.toSet().size, "$name has duplicate flag values: $values")
        }
    }
}
