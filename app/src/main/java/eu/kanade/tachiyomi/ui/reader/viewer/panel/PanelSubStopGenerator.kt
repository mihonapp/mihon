package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap

interface PanelSubStopGenerator {
    /**
     * Returns ordered sub-stops for [panel], or an empty list if it doesn't need any
     * (the panel itself is the only stop). When non-empty, the last stop is always the
     * full [panel] bounds. [cropPanel] lazily crops the panel out of the full-resolution
     * page bitmap, for generators that need to inspect panel content (e.g. OCR).
     */
    suspend fun generate(panel: PanelRect, direction: PanelDirection, cropPanel: suspend () -> Bitmap?): List<PanelRect>
}

object GeometricPanelSubStopGenerator : PanelSubStopGenerator {

    private const val WIDE_ASPECT_THRESHOLD = 2f
    private const val STOP_WIDTH_FRACTION = 0.45f
    private val STOP_CENTERS = listOf(1f / 6f, 3f / 6f, 5f / 6f)

    override suspend fun generate(
        panel: PanelRect,
        direction: PanelDirection,
        cropPanel: suspend () -> Bitmap?,
    ): List<PanelRect> {
        if (panel.height <= 0f || panel.width / panel.height < WIDE_ASPECT_THRESHOLD) return emptyList()

        val stopWidth = panel.width * STOP_WIDTH_FRACTION
        val stops = STOP_CENTERS.map { fraction ->
            val centerX = panel.left + panel.width * fraction
            PanelRect(
                left = (centerX - stopWidth / 2f).coerceAtLeast(panel.left),
                top = panel.top,
                right = (centerX + stopWidth / 2f).coerceAtMost(panel.right),
                bottom = panel.bottom,
            )
        }
        val ordered = if (direction == PanelDirection.RTL) stops.reversed() else stops
        return ordered + panel
    }
}
