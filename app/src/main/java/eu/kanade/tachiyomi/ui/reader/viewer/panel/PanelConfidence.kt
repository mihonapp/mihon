package eu.kanade.tachiyomi.ui.reader.viewer.panel

object PanelConfidence {

    private const val MAX_PLAUSIBLE_PANELS = 12
    private const val MIN_COVERED_AREA_FRACTION = 0.15

    fun isLowConfidence(rects: List<PanelRect>): Boolean {
        if (rects.isEmpty() || rects.size > MAX_PLAUSIBLE_PANELS) return true
        val coveredArea = rects.sumOf { (it.width * it.height).toDouble() }
        return coveredArea < MIN_COVERED_AREA_FRACTION
    }
}
