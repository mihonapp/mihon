package mihon.desktop.ui.library

import kotlin.math.floor

internal data class LibraryGridMetrics(
    val columns: Int,
    val cellWidthDp: Float,
)

private data class LibraryGridBounds(
    val minimum: Float,
    val maximum: Float,
    val default: Float,
    val preferenceScale: Float,
) {
    companion object {
        fun forMode(mode: LibraryDisplayMode): LibraryGridBounds = when (mode) {
            LibraryDisplayMode.ComfortableGrid -> LibraryGridBounds(168f, 224f, 180f, 1f)
            LibraryDisplayMode.CompactGrid -> LibraryGridBounds(132f, 196f, 148f, 0.82f)
            LibraryDisplayMode.CoverOnly -> LibraryGridBounds(112f, 176f, 130f, 0.72f)
            LibraryDisplayMode.List -> LibraryGridBounds(0f, Float.MAX_VALUE, 0f, 1f)
        }
    }
}

internal fun calculateLibraryGridMetrics(
    mode: LibraryDisplayMode,
    availableWidthDp: Float,
    requestedWidthDp: Float,
    spacingDp: Float,
): LibraryGridMetrics {
    if (mode == LibraryDisplayMode.List) {
        return LibraryGridMetrics(columns = 1, cellWidthDp = availableWidthDp.coerceAtLeast(0f))
    }

    val bounds = LibraryGridBounds.forMode(mode)
    val requested = requestedWidthDp
        .takeIf { it.isFinite() && it > 0f }
        ?.times(bounds.preferenceScale)
        ?.coerceIn(bounds.minimum, bounds.maximum)
        ?: bounds.default
    if (!availableWidthDp.isFinite() || availableWidthDp <= 0f) {
        return LibraryGridMetrics(columns = 1, cellWidthDp = bounds.default)
    }

    val spacing = spacingDp.takeIf { it.isFinite() && it >= 0f } ?: 0f
    var columns = floor((availableWidthDp + spacing) / (requested + spacing)).toInt().coerceAtLeast(1)
    var cellWidth = calculateCellWidth(availableWidthDp, columns, spacing)

    while (cellWidth > bounds.maximum) {
        val nextWidth = calculateCellWidth(availableWidthDp, columns + 1, spacing)
        if (nextWidth < bounds.minimum) break
        columns += 1
        cellWidth = nextWidth
    }
    while (columns > 1 && cellWidth < bounds.minimum) {
        columns -= 1
        cellWidth = calculateCellWidth(availableWidthDp, columns, spacing)
    }

    return LibraryGridMetrics(
        columns = columns,
        cellWidthDp = cellWidth.coerceAtMost(availableWidthDp),
    )
}

private fun calculateCellWidth(
    availableWidthDp: Float,
    columns: Int,
    spacingDp: Float,
): Float = (availableWidthDp - spacingDp * (columns - 1)) / columns
