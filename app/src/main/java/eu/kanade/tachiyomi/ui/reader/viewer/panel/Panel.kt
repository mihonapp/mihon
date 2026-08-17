package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlinx.serialization.Serializable

enum class PanelDirection { LTR, RTL }

@Serializable
data class PanelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val FULL_PAGE = PanelRect(0f, 0f, 1f, 1f)
    }
}

@Serializable
data class Panel(
    val bounds: PanelRect,
    val subStops: List<PanelRect> = emptyList(),
)

@Serializable
data class PanelPageData(val panels: List<Panel>)

/**
 * Flattens each panel's substops (or its own bounds, if it has none) into one ordered
 * navigation list, ending with a full-page reveal — after stepping through a page's panels
 * zoomed in, the next tap shows the whole page before advancing to the next one. Skipped when
 * the only stop is already the full page (the detector's no-panels-found fallback), so the
 * reader never taps once for a visually identical stop.
 */
fun List<Panel>.flattenToStops(): List<PanelRect> {
    val stops = flatMap { panel -> panel.subStops.ifEmpty { listOf(panel.bounds) } }
    return if (stops.size == 1 && stops.single() == PanelRect.FULL_PAGE) {
        stops
    } else {
        stops + PanelRect.FULL_PAGE
    }
}
