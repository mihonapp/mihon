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
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

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
 * navigation list, optionally bracketed with a full-page reveal — [showIntro] shows the whole
 * page before stepping into the first panel, [showOutro] shows it again after the last one,
 * before turning the page. Skipped entirely when the only stop is already the full page (the
 * detector's no-panels-found fallback), so the reader never taps once for a visually identical
 * stop no matter how intro/outro are configured.
 */
fun List<Panel>.flattenToStops(showIntro: Boolean = false, showOutro: Boolean = true): List<PanelRect> {
    val stops = flatMap { panel -> panel.subStops.ifEmpty { listOf(panel.bounds) } }
    if (stops.size == 1 && stops.single() == PanelRect.FULL_PAGE) return stops
    val withIntro = if (showIntro) listOf(PanelRect.FULL_PAGE) + stops else stops
    return if (showOutro) withIntro + PanelRect.FULL_PAGE else withIntro
}
