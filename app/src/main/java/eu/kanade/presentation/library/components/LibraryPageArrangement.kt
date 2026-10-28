package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
internal class LibraryPageArrangement(
    override val spacing: Dp = 0.dp,
    private val slots: Int = 0,
) : Arrangement.HorizontalOrVertical {

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val slotSizes = reserveSlots(sizes)
        val positions = IntArray(slotSizes.size)
        with(if (slotSizes.size == 1) Arrangement.Center else Arrangement.SpaceBetween) {
            arrange(totalSize, slotSizes, positions)
        }
        positions.copyInto(outPositions, endIndex = sizes.size)
    }

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        val slotSizes = reserveSlots(sizes)
        val positions = IntArray(slotSizes.size)
        with(if (slotSizes.size == 1) Arrangement.Center else Arrangement.SpaceBetween) {
            arrange(totalSize, slotSizes, layoutDirection, positions)
        }
        positions.copyInto(outPositions, endIndex = sizes.size)
    }

    private fun reserveSlots(sizes: IntArray): IntArray {
        val rowSize = sizes.maxOrNull() ?: 0
        return IntArray(maxOf(slots, sizes.size)) { sizes.getOrElse(it) { rowSize } }
    }
}
