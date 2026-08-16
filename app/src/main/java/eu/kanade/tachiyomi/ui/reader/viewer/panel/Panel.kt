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

fun List<Panel>.flattenToStops(): List<PanelRect> {
    return flatMap { panel -> panel.subStops.ifEmpty { listOf(panel.bounds) } }
}
