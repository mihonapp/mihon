package eu.kanade.presentation.library.components

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryPageArrangementTest {

    @Test
    fun `stretch distributes spare height without changing page capacity`() {
        val arrangement = LibraryPageArrangement(4.dp)
        val positions = IntArray(3)
        with(arrangement) { Density(1f).arrange(400, intArrayOf(100, 100, 100), positions) }

        positions.toList() shouldBe listOf(0, 150, 300)
        arrangement.spacing shouldBe 4.dp
        libraryPageCapacity(307, 100, 4, 3, 0) shouldBe 6
    }

    @Test
    fun `a single row is centered`() {
        val positions = IntArray(1)
        with(LibraryPageArrangement()) { Density(1f).arrange(400, intArrayOf(100), positions) }
        positions.toList() shouldBe listOf(150)
    }

    @Test
    fun `manual column spacing follows layout direction`() {
        val positions = IntArray(3)
        with(LibraryPageArrangement(4.dp)) {
            Density(1f).arrange(500, intArrayOf(100, 100, 100), LayoutDirection.Rtl, positions)
        }
        positions.toList() shouldBe listOf(400, 200, 0)
    }

    @Test
    fun `partial pages retain the row positions of a full page`() {
        val arrangement = LibraryPageArrangement(4.dp, slots = 3)
        for (count in 0..3) {
            val positions = IntArray(count)
            with(arrangement) { Density(1f).arrange(400, IntArray(count) { 100 }, positions) }
            positions.toList() shouldBe listOf(0, 150, 300).take(count)
        }
    }

    @Test
    fun `partial rows retain empty column slots in both layout directions`() {
        val arrangement = LibraryPageArrangement(4.dp, slots = 3)
        for (direction in LayoutDirection.entries) {
            val positions = IntArray(2)
            with(arrangement) { Density(1f).arrange(500, intArrayOf(100, 100), direction, positions) }
            positions.toList() shouldBe if (direction == LayoutDirection.Ltr) listOf(0, 200) else listOf(400, 200)
        }
    }
}
